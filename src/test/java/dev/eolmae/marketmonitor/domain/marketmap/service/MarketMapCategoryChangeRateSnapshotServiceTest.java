package dev.eolmae.marketmonitor.domain.marketmap.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepositoryCustom.SnapshotRetentionSummary;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketValueTierThresholdRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketMapCategoryChangeRateSnapshotServiceTest {

    private final MarketMapCategoryChangeRateSnapshotRepository marketMapCategoryChangeRateSnapshotRepository =
            mock(MarketMapCategoryChangeRateSnapshotRepository.class);
    private final MarketValueTierThresholdRepository marketValueTierThresholdRepository =
            mock(MarketValueTierThresholdRepository.class);
    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService =
            new MarketMapCategoryChangeRateSnapshotService(
                    marketMapCategoryChangeRateSnapshotRepository, marketValueTierThresholdRepository);

    @Test
    void cleanupSnapshotsBefore_드라이런이면_삭제_메서드를_호출하지_않는다() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 10, 0, 0);
        when(marketMapCategoryChangeRateSnapshotRepository.summarizeSnapshotsToDelete(eq(cutoff), any(), anyInt()))
                .thenReturn(new SnapshotRetentionSummary(10, 12, cutoff, cutoff, List.of(LocalTime.of(9, 0))));

        marketMapCategoryChangeRateSnapshotService.cleanupSnapshotsBefore(cutoff, true);

        verify(marketMapCategoryChangeRateSnapshotRepository, never()).deleteSnapshotsBefore(any(), any());
    }

    @Test
    void cleanupSnapshotsBefore_실삭제_모드면_삭제_메서드를_호출한다() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 10, 0, 0);
        when(marketMapCategoryChangeRateSnapshotRepository.summarizeSnapshotsToDelete(eq(cutoff), any(), anyInt()))
                .thenReturn(new SnapshotRetentionSummary(10, 12, cutoff, cutoff, List.of(LocalTime.of(9, 0))));

        marketMapCategoryChangeRateSnapshotService.cleanupSnapshotsBefore(cutoff, false);

        verify(marketMapCategoryChangeRateSnapshotRepository)
                .deleteSnapshotsBefore(eq(cutoff), eq(LocalTime.of(15, 30)));
    }
}
