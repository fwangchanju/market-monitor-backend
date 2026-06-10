package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.domain.stock.StockMaster;
import dev.eolmae.marketmonitor.domain.stock.StockMasterCacheService;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.StockInfoRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.StockInfoResponse;
import dev.eolmae.marketmonitor.domain.stock.enums.Board;
import dev.eolmae.marketmonitor.domain.stock.repository.StockMasterRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockInfoCollector {

    // ka10099: 종목정보 리스트 (종목정보 카테고리)

    private final KiwoomApiClient kiwoomApiClient;
    private final StockMasterRepository stockMasterRepository;
    private final StockMasterCacheService stockMasterCacheService;

    @Transactional
    public void sync() {
        Set<String> fetchedCodes = new HashSet<>();

        try {
            for (Board board : Board.values()) {
                fetchedCodes.addAll(syncForMarket(board));
            }
        } catch (EscalateException e) {
            throw e;
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.STOCK_MASTER_SYNC_FAILED, e);
        }

        // API에서 더 이상 조회되지 않는 종목은 비활성화
        List<StockMaster> allStocks = stockMasterRepository.findAll();
        for (StockMaster stock : allStocks) {
            if (stock.isActive() && !fetchedCodes.contains(stock.getStockCode())) {
                stock.markInactive();
                log.debug("종목 비활성화: stockCode={}, stockName={}", stock.getStockCode(), stock.getStockName());
            }
        }

        stockMasterCacheService.evict();
        log.info("종목 마스터 동기화 완료: 조회 종목 수={}", fetchedCodes.size());
    }

    private Set<String> syncForMarket(Board board) {
        Exchange marketType = Exchange.valueOf(board.name());
        String mrktTp =
                switch (board) {
                    case KOSPI -> MrktTp.KOSPI.value;
                    case KOSDAQ -> MrktTp.KOSDAQ.value;
                };
        Set<String> fetchedCodes = new HashSet<>(); // TODO 이거 꼭 Set 이어야됨?

        var request = new StockInfoRequest(mrktTp);
        StockInfoResponse response = kiwoomApiClient.post(request, StockInfoResponse.class);

        if (response.list() == null) { // TODO Set.of() 로 리턴하고 여기 이후에서 객체 생성해야 이득아님?
            return fetchedCodes;
        }

        for (StockInfoResponse.StockItem item : response.list()) {
            String stockCode = item.code() != null ? item.code().trim() : ""; // TODO 옵셔널처리
            if (stockCode.isEmpty()) { // TODO 위에서 3항연산자로 null 인거 없도록 만들어놓고 이건 뭐하는짓임?
                continue;
            }

            // 키움 API가 종목명에 공백을 포함해 반환하는 경우가 있어 제거
            String stockName = item.name() != null ? item.name().replace(" ", "") : ""; // TODO 옵셔널
            String marketCode = item.marketCode();
            Long listCount = NumberParser.parseLong(item.listCount());
            BigDecimal lastPrice = NumberParser.parseBigDecimal(item.lastPrice());
            fetchedCodes.add(stockCode);

            StockMaster existing = stockMasterRepository.findById(stockCode).orElse(null);
            if (existing == null) {
                stockMasterRepository.save(
                        StockMaster.create(stockCode, stockName, marketType, marketCode, listCount, lastPrice));
            } else if (!existing.getStockName().equals(stockName) || !existing.isActive()) {
                existing.update(stockName, marketType, marketCode, listCount, lastPrice);
            }
        }

        log.debug("종목 마스터 시장별 동기화 완료: market={}, count={}", marketType, fetchedCodes.size());
        return fetchedCodes;
    }

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("10"); // ka10099 mrkt_tp (ka20001과 코드 체계 다름)
        final String value;

        MrktTp(String value) {
            this.value = value;
        }
    }
}
