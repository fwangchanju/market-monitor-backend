package dev.eolmae.marketmonitor.domain.stock.scheduler;

import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.util.KstClock;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 스냅샷 정리 배치: sector_price_snapshot, market_map_category_change_rate_snapshot 두 테이블에서
 * cutoff(오늘 KST 기준 RETENTION_DAYS일 전 00:00)보다 오래됐으면서 장마감(15:30) 시각이 아닌 데이터를 지운다.
 * 두 테이블은 서로 다른 도메인(stock/marketmap) 소관이라 삭제 로직은 각 도메인의 서비스에 두고,
 * 이 스케줄러는 언제 돌지와 테이블별 독립 실행(하나가 실패해도 다른 하나는 계속)만 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotRetentionScheduler {

    private static final int RETENTION_DAYS = 30;
    private static final String KST_ZONE_ID = "Asia/Seoul";

    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;
    private final EscalationPublisher escalationPublisher;

    @Value("${market-monitor.retention.dry-run:true}")
    private boolean dryRun;

    @Scheduled(cron = "0 0 4 * * *", zone = KST_ZONE_ID)
    public void cleanupSnapshots() {
        LocalDateTime cutoff = calculateCutoff(KstClock.now().toLocalDate());
        log.info("스냅샷 정리 배치 시작: cutoff={}, dryRun={}", cutoff, dryRun);

        run("섹터가격스냅샷정리", () -> sectorPriceSnapshotService.cleanupSnapshotsBefore(cutoff, dryRun));
        run("카테고리등락률스냅샷정리", () -> marketMapCategoryChangeRateSnapshotService.cleanupSnapshotsBefore(cutoff, dryRun));

        log.info("스냅샷 정리 배치 종료");
    }

    /** cutoff 경계 — today 기준 RETENTION_DAYS일 전 자정. 그 경계일(30일째) 데이터는 남기고 그 이전만 삭제 대상. */
    static LocalDateTime calculateCutoff(LocalDate today) {
        return today.minusDays(RETENTION_DAYS).atStartOfDay();
    }

    // CollectionScheduler.run()과 동일한 격리 패턴 — 한 테이블 정리가 실패해도 다른 테이블 정리는 계속한다.
    private void run(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            escalationPublisher.report(EscalateException.wrap(ErrorCode.SNAPSHOT_RETENTION_FAILED, e, taskName));
        }
    }
}
