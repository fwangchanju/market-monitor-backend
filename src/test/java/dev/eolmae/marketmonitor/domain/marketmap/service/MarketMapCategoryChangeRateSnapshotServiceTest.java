package dev.eolmae.marketmonitor.domain.marketmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepositoryCustom.SnapshotRetentionSummary;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketValueTierThresholdRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketMapCategoryChangeRateSnapshotServiceTest {

    private static final LocalTime WINDOW_START = LocalTime.of(15, 30);
    private static final LocalTime WINDOW_END = LocalTime.of(15, 40);

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
        when(marketMapCategoryChangeRateSnapshotRepository.findMarketSnapshotTimesInWindow(
                        cutoff, WINDOW_START, WINDOW_END))
                .thenReturn(List.of());
        when(marketMapCategoryChangeRateSnapshotRepository.summarizeSnapshotsToDelete(eq(cutoff), any(), anyInt()))
                .thenReturn(emptySummary());

        marketMapCategoryChangeRateSnapshotService.cleanupSnapshotsBefore(cutoff, true);

        verify(marketMapCategoryChangeRateSnapshotRepository, never()).deleteSnapshotsBefore(any(), any());
    }

    @Test
    void cleanupSnapshotsBefore_실삭제_모드면_보존_대상을_넘겨_삭제_메서드를_호출한다() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 10, 0, 0);
        LocalDateTime kospiTime = LocalDateTime.of(2026, 8, 9, 15, 35);
        LocalDateTime kospiEarlier = LocalDateTime.of(2026, 8, 9, 15, 30);
        when(marketMapCategoryChangeRateSnapshotRepository.findMarketSnapshotTimesInWindow(
                        cutoff, WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        new MarketSnapshotTime(Market.KOSPI, kospiEarlier),
                        new MarketSnapshotTime(Market.KOSPI, kospiTime)));
        when(marketMapCategoryChangeRateSnapshotRepository.summarizeSnapshotsToDelete(eq(cutoff), any(), anyInt()))
                .thenReturn(emptySummary());

        marketMapCategoryChangeRateSnapshotService.cleanupSnapshotsBefore(cutoff, false);

        // 두 후보 중 늦은 시각(15:35)만 보존 대상으로 넘어간다.
        verify(marketMapCategoryChangeRateSnapshotRepository)
                .deleteSnapshotsBefore(eq(cutoff), eq(List.of(new MarketSnapshotTime(Market.KOSPI, kospiTime))));
    }

    // 2단계(순수 자바 함수) — selectRetainedSnapshotTimes.

    @Test
    void selectRetainedSnapshotTimes_1530과_1535가_둘다_있으면_1535만_고른다() {
        LocalDateTime time1530 = LocalDateTime.of(2026, 8, 9, 15, 30);
        LocalDateTime time1535 = LocalDateTime.of(2026, 8, 9, 15, 35);
        List<MarketSnapshotTime> candidates =
                List.of(new MarketSnapshotTime(Market.KOSPI, time1530), new MarketSnapshotTime(Market.KOSPI, time1535));

        List<MarketSnapshotTime> retained = MarketMapCategoryChangeRateSnapshotService.selectRetainedSnapshotTimes(
                candidates, WINDOW_START, WINDOW_END);

        assertThat(retained).containsExactly(new MarketSnapshotTime(Market.KOSPI, time1535));
    }

    @Test
    void selectRetainedSnapshotTimes_1535가_없고_1530만_있으면_1530을_고른다() {
        LocalDateTime time1530 = LocalDateTime.of(2026, 8, 9, 15, 30);
        List<MarketSnapshotTime> candidates = List.of(new MarketSnapshotTime(Market.KOSPI, time1530));

        List<MarketSnapshotTime> retained = MarketMapCategoryChangeRateSnapshotService.selectRetainedSnapshotTimes(
                candidates, WINDOW_START, WINDOW_END);

        assertThat(retained).containsExactly(new MarketSnapshotTime(Market.KOSPI, time1530));
    }

    @Test
    void selectRetainedSnapshotTimes_윈도우가_통째로_비면_그_날짜는_결과에_없다() {
        List<MarketSnapshotTime> retained = MarketMapCategoryChangeRateSnapshotService.selectRetainedSnapshotTimes(
                List.of(), WINDOW_START, WINDOW_END);

        assertThat(retained).isEmpty();
    }

    @Test
    void selectRetainedSnapshotTimes_마켓별로_다른_시각이_잡힌다() {
        LocalDateTime kospiTime = LocalDateTime.of(2026, 8, 9, 15, 35);
        LocalDateTime kosdaqTime = LocalDateTime.of(2026, 8, 9, 15, 30);
        List<MarketSnapshotTime> candidates = List.of(
                new MarketSnapshotTime(Market.KOSPI, kospiTime), new MarketSnapshotTime(Market.KOSDAQ, kosdaqTime));

        List<MarketSnapshotTime> retained = MarketMapCategoryChangeRateSnapshotService.selectRetainedSnapshotTimes(
                candidates, WINDOW_START, WINDOW_END);

        assertThat(retained)
                .containsExactlyInAnyOrder(
                        new MarketSnapshotTime(Market.KOSPI, kospiTime),
                        new MarketSnapshotTime(Market.KOSDAQ, kosdaqTime));
    }

    @Test
    void selectRetainedSnapshotTimes_윈도우_밖_시각은_후보에_안_들어온다() {
        LocalDateTime beforeWindow = LocalDateTime.of(2026, 8, 9, 15, 25);
        LocalDateTime afterWindow = LocalDateTime.of(2026, 8, 9, 15, 40);
        List<MarketSnapshotTime> candidates = List.of(
                new MarketSnapshotTime(Market.KOSPI, beforeWindow), new MarketSnapshotTime(Market.KOSPI, afterWindow));

        List<MarketSnapshotTime> retained = MarketMapCategoryChangeRateSnapshotService.selectRetainedSnapshotTimes(
                candidates, WINDOW_START, WINDOW_END);

        assertThat(retained).isEmpty();
    }

    private SnapshotRetentionSummary emptySummary() {
        return new SnapshotRetentionSummary(0, 0, null, null, List.of(), List.of(), 0, List.of());
    }
}
