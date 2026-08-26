package dev.eolmae.marketmonitor.runner;

import dev.eolmae.marketmonitor.domain.access.entity.AdminToken;
import dev.eolmae.marketmonitor.domain.access.properties.AdminProperties;
import dev.eolmae.marketmonitor.domain.access.repository.AdminTokenRepository;
import dev.eolmae.marketmonitor.domain.stock.collector.HoldingsSyncService;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockBackfillService;
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
    private final AdminTokenRepository adminTokenRepository;
    private final AdminProperties adminProperties;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 전종목 캐싱
        loadStockInfoCache();

        // 2·3·4. 보유종목 동기화/관심종목 캐시/백필: 관심종목 구조 정리 전까지 비활성화
        // syncHoldings();
        // List<WatchStock> watchStockCache = getWatchStockCache();
        // for (WatchStock watchStock : watchStockCache) {
        //     watchStockBackfillService.backfill(watchStock);
        // }
        // log.info("[startup] 백필 요청 완료: {}종목 (비동기 처리 중)", watchStockCache.size());

        // 5. 관리자 토큰 동기화
        syncAdminTokens();
    }

    private void loadStockInfoCache() {

        try {
            stockInfoCacheService.getCache();
            log.info("[startup] 전체 종목 정보 캐싱 완료");
        } catch (Exception e) {
            log.error("[startup] 전체 종목 정보 캐싱 실패", e);
        }
    }

    private void syncHoldings() {

        try {
            holdingsSyncService.sync();
            log.info("[startup] 보유종목 동기화 완료");
        } catch (Exception e) {
            log.error("[startup] 보유종목 동기화 실패", e);
        }
    }

    private List<WatchStock> getWatchStockCache() {

        try {
            List<WatchStock> watchStockCache = watchStockCacheService.getCache();
            log.info("[startup] 관심종목 캐시 로드 완료: {}종목", watchStockCache.size());

            return watchStockCache;
        } catch (Exception e) {
            log.error("[startup] 관심종목 캐시 로드 실패", e);
            return List.of();
        }
    }

    private void syncAdminTokens() {
        try {
            for (String token : adminProperties.tokens()) {
                adminTokenRepository
                        .findById(token)
                        .ifPresentOrElse(existing -> {}, () -> adminTokenRepository.save(AdminToken.create(token)));
            }
            log.info("[startup] 관리자 토큰 동기화 완료");
        } catch (Exception e) {
            log.error("[startup] 관리자 토큰 동기화 실패", e);
        }
    }
}
