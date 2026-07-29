package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.AccountBalanceRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.AccountBalanceResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.enums.RegisterBy;
import dev.eolmae.marketmonitor.domain.stock.repository.WatchStockRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockBackfillService;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockCacheService;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingsSyncService {

    private final KiwoomApiClient kiwoomApiClient;
    private final StockInfoCacheService stockInfoCacheService;
    private final WatchStockRepository watchStockRepository;
    private final WatchStockCacheService watchStockCacheService;
    private final WatchStockBackfillService watchStockBackfillService;

    /**
     * kt00018 보유종목 동기화.
     * - 신규 보유종목 → watch_stock에 HOLDINGS로 등록
     * - 기존 HOLDINGS 중 보유 없는 종목 → 삭제
     * - 평가금액(evltAmt) 내림차순 holdingRank 1,2,3… 부여/갱신
     */
    @Transactional
    public void sync() {
        AccountBalanceResponse response =
                kiwoomApiClient.post(AccountBalanceRequest.defaults(), AccountBalanceResponse.class);

        if (response.holdings() == null || response.holdings().isEmpty()) {
            log.info("보유종목 없음 — HOLDINGS 전체 삭제");
            watchStockRepository.deleteByRegisterBy(RegisterBy.HOLDINGS);
            watchStockCacheService.evict();
            return;
        }

        // 평가금액(evltAmt) 내림차순 정렬
        List<AccountBalanceResponse.HoldingItem> sortedHoldings = response.holdings().stream()
                .sorted((a, b) -> KiwoomValueParser.parseBigDecimal(b.evltAmt())
                        .compareTo(KiwoomValueParser.parseBigDecimal(a.evltAmt())))
                .toList();

        // 보유 없는 HOLDINGS 삭제
        watchStockRepository.deleteByRegisterByAndStockCodeNotIn(
                RegisterBy.HOLDINGS,
                sortedHoldings.stream()
                        .map(AccountBalanceResponse.HoldingItem::stockCode)
                        .toList());

        // stockCode는 registerBy와 무관하게 테이블 전체에서 유일해야 해서(uk_watch_stock_stock),
        // 대표종목 지정 등으로 USER로 전환된 종목까지 함께 조회해 신규 insert 시 중복을 피한다.
        Map<String, WatchStock> existingWatchStocks = watchStockRepository.findAll().stream()
                .collect(Collectors.toMap(WatchStock::getStockCode, Function.identity()));
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();

        int rank = 1;
        for (AccountBalanceResponse.HoldingItem holding : sortedHoldings) {
            String stockCode = holding.stockCode();
            int holdingRank = rank++;

            WatchStock existing = existingWatchStocks.get(stockCode);
            if (existing != null) {
                if (existing.getRegisterBy() == RegisterBy.HOLDINGS) {
                    existing.updateHoldingRank(holdingRank);
                }
                continue;
            }

            if (stockInfoCache.containsKey(stockCode)) {
                WatchStock saved = watchStockRepository.save(WatchStock.createHolding(stockCode, holdingRank));
                watchStockBackfillService.backfill(saved);
            } else {
                log.warn("보유종목이 종목 기준정보에 없음: stockCode={}", stockCode);
            }
        }

        watchStockCacheService.evict();
        log.info("보유종목 동기화 완료: 종목수={}", sortedHoldings.size());
    }
}
