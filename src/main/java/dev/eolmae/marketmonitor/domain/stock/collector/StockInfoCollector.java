package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.event.IndustryInfoCreatedEvent;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.util.Strings;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.StockInfoRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.StockInfoResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.IndustryInfo;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketmonitor.domain.stock.repository.IndustryInfoRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.StockInfoRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockInfoCollector {

    private final KiwoomApiClient kiwoomApiClient;
    private final StockInfoRepository stockInfoRepository;
    private final IndustryInfoRepository industryInfoRepository;
    private final StockInfoCacheService stockInfoCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

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

        Map<String, IndustryInfo> industryByName = syncIndustryInfo(fetchedStocks.values());

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
                    industryId(fetched, industryByName),
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
                        industryId(fetched, industryByName),
                        fetched.listCount(),
                        fetched.lastPrice()))
                .toList();
        stockInfoRepository.saveAll(newStocks);
        // 커밋 전에 비우면 evict~커밋 사이에 다른 스레드가 캐시를 재적재해 옛 데이터를 캐시에 굳힐 수
        // 있다 — 그래서 evict는 이 트랜잭션이 커밋된 뒤로 미룬다.
        evictCacheAfterCommit();

        log.info("종목 정보 동기화 완료: 조회 종목 수={}", fetchedCount);
    }

    private Map<String, IndustryInfo> syncIndustryInfo(Iterable<FetchStockInfo> fetchedStocks) {
        Set<String> industryNames = new HashSet<>();
        for (FetchStockInfo fetched : fetchedStocks) {
            if (StockMarketCode.isOrdinaryShare(fetched.marketCode())
                    && fetched.categoryName() != null
                    && !fetched.categoryName().isBlank()) {
                industryNames.add(fetched.categoryName());
            }
        }
        if (industryNames.isEmpty()) {
            return Map.of();
        }

        Set<String> existingNames = industryInfoRepository.findByNameIn(industryNames).stream()
                .map(IndustryInfo::getName)
                .collect(Collectors.toSet());
        for (String name : industryNames.stream().sorted().toList()) {
            if (existingNames.contains(name)) {
                continue;
            }
            List<String> insertedNames = jdbcTemplate.query(
                    "INSERT INTO industry_info (name) VALUES (?) ON CONFLICT (name) DO NOTHING RETURNING name",
                    (resultSet, rowNumber) -> resultSet.getString("name"),
                    name);
            insertedNames.forEach(
                    insertedName -> eventPublisher.publishEvent(new IndustryInfoCreatedEvent(insertedName)));
        }
        return industryInfoRepository.findByNameIn(industryNames).stream()
                .collect(Collectors.toMap(IndustryInfo::getName, Function.identity()));
    }

    private Long industryId(FetchStockInfo fetched, Map<String, IndustryInfo> industryByName) {
        if (!StockMarketCode.isOrdinaryShare(fetched.marketCode())
                || fetched.categoryName() == null
                || fetched.categoryName().isBlank()) {
            return null;
        }
        IndustryInfo industry = industryByName.get(fetched.categoryName());
        return industry == null ? null : industry.getId();
    }

    // StockInfoCollectorTest처럼 활성 트랜잭션 없이 도는 단위 테스트에서는
    // registerSynchronization()이 IllegalStateException을 던지므로, 그런 경우엔 즉시 evict한다.
    private void evictCacheAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            stockInfoCacheService.evict();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stockInfoCacheService.evict();
            }
        });
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
