package dev.eolmae.marketry.domain.stock.scheduler;

import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.common.util.KstClock;
import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 스냅샷 정리 배치: 11일 전 하루에서 시장별 종가 구간 latest의 종목 행만 남긴다.
 * 실패한 날짜는 서비스에 그 날짜를 지정해 다시 처리한다. 더 오래된 날짜를 자동으로 재처리하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotRetentionScheduler {

    private static final int RETENTION_DAYS = 10;
    private static final String KST_ZONE_ID = "Asia/Seoul";

    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final EscalationPublisher escalationPublisher;

    @Scheduled(cron = "0 0 4 * * *", zone = KST_ZONE_ID)
    public void cleanupSnapshots() {
        LocalDate targetDate = calculateTargetDate(KstClock.now().toLocalDate());
        log.info("스냅샷 정리 배치 시작: date={}", targetDate);

        run("섹터가격스냅샷정리", () -> sectorPriceSnapshotService.cleanupSnapshotsForDate(targetDate));

        log.info("스냅샷 정리 배치 종료");
    }

    /** 10일 전 날짜까지는 모두 보존하고, 그 바로 이전 날짜 하루만 정리한다. */
    static LocalDate calculateTargetDate(LocalDate today) {
        return today.minusDays(RETENTION_DAYS + 1);
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
