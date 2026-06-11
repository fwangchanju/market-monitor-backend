package dev.eolmae.marketmonitor.runner;

import dev.eolmae.marketmonitor.domain.stock.collector.HoldingsSyncService;
import dev.eolmae.marketmonitor.domain.stock.collector.WatchStockBackfillService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockCacheService;
import java.util.List;
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
    private final WatchStockCacheService watchStockCacheService;
    private final WatchStockBackfillService watchStockBackfillService;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 종목 마스터 캐시 워밍
        try {
            stockInfoCacheService.findAllAsMap();
            log.info("[startup] 종목 마스터 캐시 로드 완료");
        } catch (Exception e) {
            log.error("[startup] 종목 마스터 캐시 로드 실패", e);
        }

        // 2. kt00018 동기화 — HOLDINGS watch_stock 반영 + HoldingsCache 업데이트
        try {
            holdingsSyncService.sync();
            log.info("[startup] 보유종목 동기화 완료");
        } catch (Exception e) {
            log.error("[startup] 보유종목 동기화 실패 (Kiwoom API 미설정 시 정상)", e);
        }

        // 3. 관심종목 캐시 워밍 (2번 완료 후 최신 watch_stock 반영)
        List<String> watchCodes;
        try {
            watchCodes = watchStockCacheService.findDistinctStockCodes();
            log.info("[startup] 관심종목 캐시 로드 완료: {}종목", watchCodes.size());
        } catch (Exception e) {
            log.error("[startup] 관심종목 캐시 로드 실패", e);
            return;
        }

        // 4. 전체 watch_stock 백필 가드 확인 (비동기 — 기동 지연 최소화)
        for (String stockCode : watchCodes) {
            watchStockBackfillService.backfill(stockCode);
        }
        log.info("[startup] 백필 요청 완료: {}종목 (비동기 처리 중)", watchCodes.size());
    }
}
