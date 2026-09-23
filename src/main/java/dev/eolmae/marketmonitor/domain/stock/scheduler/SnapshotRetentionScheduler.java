package dev.eolmae.marketmonitor.domain.stock.scheduler;

import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.util.KstClock;
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
 * 스냅샷 정리 배치: sector_price_snapshot 테이블에서 cutoff(오늘 KST 기준 RETENTION_DAYS일 전 00:00)보다
 * 오래된 데이터 중, 그 날짜·마켓의 보존 윈도우([15:30, 15:40))에서 가장 늦은 시각(latest)이 아닌 것을
 * 지운다. 삭제 로직은 도메인 서비스에 두고, 이 스케줄러는 언제 돌지만 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotRetentionScheduler {

    private static final int RETENTION_DAYS = 10;
    private static final String KST_ZONE_ID = "Asia/Seoul";

    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final EscalationPublisher escalationPublisher;

    @Value("${market-monitor.retention.dry-run:true}")
    private boolean dryRun;

    @Scheduled(cron = "0 0 4 * * *", zone = KST_ZONE_ID)
    public void cleanupSnapshots() {
        LocalDateTime cutoff = calculateCutoff(KstClock.now().toLocalDate());
        log.info("스냅샷 정리 배치 시작: cutoff={}, dryRun={}", cutoff, dryRun);

        run("섹터가격스냅샷정리", () -> sectorPriceSnapshotService.cleanupSnapshotsBefore(cutoff, dryRun));

        log.info("스냅샷 정리 배치 종료");
    }

    /** cutoff 경계 — today 기준 RETENTION_DAYS일 전 자정. 그 경계일(10일째) 데이터는 남기고 그 이전만 삭제 대상. */
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
