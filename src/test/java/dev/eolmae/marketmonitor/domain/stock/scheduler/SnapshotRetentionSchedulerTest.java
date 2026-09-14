package dev.eolmae.marketmonitor.domain.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SnapshotRetentionSchedulerTest {

    @Test
    void calculateCutoff_오늘_기준_10일_전_자정을_반환한다() {
        LocalDate today = LocalDate.of(2026, 9, 9);

        LocalDateTime cutoff = SnapshotRetentionScheduler.calculateCutoff(today);

        assertThat(cutoff).isEqualTo(LocalDateTime.of(2026, 8, 30, 0, 0));
    }

    @Test
    void calculateCutoff_월_경계를_넘어도_정확히_10일_전이다() {
        LocalDate today = LocalDate.of(2026, 3, 1);

        LocalDateTime cutoff = SnapshotRetentionScheduler.calculateCutoff(today);

        assertThat(cutoff).isEqualTo(LocalDateTime.of(2026, 2, 19, 0, 0));
    }
}
