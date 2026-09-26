package dev.eolmae.marketmonitor.runner;

import dev.eolmae.marketmonitor.domain.stock.collector.HoldingsSyncService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {

    private final StockInfoCacheService stockInfoCacheService;
    private final HoldingsSyncService holdingsSyncService;

    // 관심종목 구조 정리 전까지 run()의 호출부가 주석 처리돼 있어 미사용 상태(docs/backlog.md).
    @SuppressWarnings("UnusedVariable")
    private final WatchStockBackfillService watchStockBackfillService;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 전종목 캐싱
        loadStockInfoCache();

        // 2·3·4. 보유종목 동기화/관심종목 캐시/백필: 관심종목 구조 정리 전까지 비활성화
        // syncHoldings();
    }

    private void loadStockInfoCache() {

        try {
            stockInfoCacheService.getCache();
            log.info("[startup] 전체 종목 정보 캐싱 완료");
        } catch (Exception e) {
            log.error("[startup] 전체 종목 정보 캐싱 실패", e);
        }
    }

    // 관심종목 구조 정리 전까지 run()의 호출부가 주석 처리돼 있어 미사용 상태(docs/backlog.md).
    @SuppressWarnings("UnusedMethod")
    private void syncHoldings() {

        try {
            holdingsSyncService.sync();
            log.info("[startup] 보유종목 동기화 완료");
        } catch (Exception e) {
            log.error("[startup] 보유종목 동기화 실패", e);
        }
    }
}
