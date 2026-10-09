package dev.eolmae.marketry.domain.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SnapshotRetentionSchedulerTest {

    @Test
    void 정리_대상은_오늘_기준_11일_전_하루다() {
        LocalDate today = LocalDate.of(2026, 9, 9);

        LocalDate targetDate = SnapshotRetentionScheduler.calculateTargetDate(today);

        assertThat(targetDate).isEqualTo(LocalDate.of(2026, 8, 29));
    }

    @Test
    void 월_경계를_넘어도_11일_전_날짜를_선택한다() {
        LocalDate today = LocalDate.of(2026, 3, 1);

        LocalDate targetDate = SnapshotRetentionScheduler.calculateTargetDate(today);

        assertThat(targetDate).isEqualTo(LocalDate.of(2026, 2, 18));
    }

    @Test
    void 연도_경계를_넘어도_11일_전_날짜를_선택한다() {
        assertThat(SnapshotRetentionScheduler.calculateTargetDate(LocalDate.of(2026, 1, 5)))
                .isEqualTo(LocalDate.of(2025, 12, 25));
    }
}
