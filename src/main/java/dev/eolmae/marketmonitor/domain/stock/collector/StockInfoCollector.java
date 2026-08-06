package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.util.Strings;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.StockInfoRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.StockInfoResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketmonitor.domain.stock.repository.StockInfoRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockInfoCollector {

    // ka10099: 종목정보 리스트 (종목정보 카테고리)
    private static final String UNCATEGORIZED = "미분류";

    private final KiwoomApiClient kiwoomApiClient;
    private final StockInfoRepository stockInfoRepository;
    private final StockInfoCacheService stockInfoCacheService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void sync() {
        Map<String, FetchStockInfo> fetchedStocks = new HashMap<>();

        try {
            for (Market market : Market.values()) {
                collectMarket(market, fetchedStocks);
            }
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.STOCK_INFO_SYNC_FAILED, e);
        }

        int fetchedCount = fetchedStocks.size();

        for (StockInfo existing : stockInfoRepository.findAll()) {
            FetchStockInfo fetched = fetchedStocks.remove(existing.getStockCode());

            if (fetched == null) {
                if (existing.isActive()) {
                    existing.markInactive();
                    log.debug("종목 비활성화: stockCode={}, stockName={}", existing.getStockCode(), existing.getStockName());
                }
                continue;
            }

            existing.update(
                    fetched.stockName(),
                    fetched.market(),
                    fetched.marketCode(),
                    fetched.categoryName(),
                    fetched.listCount(),
                    fetched.lastPrice());
        }

        // DB에 없던 신규 종목
        List<StockInfo> newStocks = fetchedStocks.values().stream()
                .map(fetched -> StockInfo.create(
                        fetched.stockCode(),
                        fetched.stockName(),
                        fetched.market(),
                        fetched.marketCode(),
                        fetched.categoryName(),
                        fetched.listCount(),
                        fetched.lastPrice()))
                .toList();
        stockInfoRepository.saveAll(newStocks);

        // 마켓맵은 주권(코스피/코스닥)만 다루므로 ELW/ETF 등은 이벤트 발행 단계에서 제외 (stock_info 저장 자체는 종류 무관하게 전부 유지)
        List<StockInfoSyncedEvent.NewStock> newStockEvents = newStocks.stream()
                .filter(stock -> StockMarketCode.isOrdinaryShare(stock.getMarketCode()))
                .map(stock -> new StockInfoSyncedEvent.NewStock(
                        stock.getStockCode(), stock.getCategoryName().isBlank() ? UNCATEGORIZED : stock.getCategoryName()))
                .toList();
        eventPublisher.publishEvent(new StockInfoSyncedEvent(newStockEvents));

        stockInfoCacheService.evict();
        log.info("종목 정보 동기화 완료: 조회 종목 수={}", fetchedCount);
    }

    private void collectMarket(Market market, Map<String, FetchStockInfo> fetchedStocks) {
        String mrktTp = MrktTp.from(market);

        var request = new StockInfoRequest(mrktTp);
        StockInfoResponse response = kiwoomApiClient.post(request, StockInfoResponse.class);

        if (response.list() == null) {
            return;
        }

        for (StockInfoResponse.StockItem item : response.list()) {
            String stockCode = Strings.trimToEmpty(item.code());
            if (stockCode.isEmpty()) {
                log.warn("종목코드 없는 항목 스킵: stockName={}", item.name());
                continue;
            }

            fetchedStocks.put(
                    stockCode,
                    new FetchStockInfo(
                            stockCode,
                            Strings.trimToEmpty(item.name()),
                            market,
                            item.marketCode(),
                            Strings.trimToEmpty(item.upName()),
                            KiwoomValueParser.parseLong(item.listCount()),
                            KiwoomValueParser.parseBigDecimal(item.lastPrice())));
        }

        log.debug("종목 정보 시장별 동기화 완료: market={}", market);
    }

    /** API 응답(StockInfoResponse.StockItem) 파싱 + market 컨텍스트를 합친, DB 비교 전 임시 보관용 */
    private record FetchStockInfo(
            String stockCode,
            String stockName,
            Market market,
            String marketCode,
            String categoryName,
            Long listCount,
            BigDecimal lastPrice) {}

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("10"); // ka10099 mrkt_tp (ka20001과 코드 체계 다름)
        final String value;

        MrktTp(String value) {
            this.value = value;
        }

        static String from(Market market) {
            return switch (market) {
                case KOSPI -> MrktTp.KOSPI.value;
                case KOSDAQ -> MrktTp.KOSDAQ.value;
            };
        }
    }
}
