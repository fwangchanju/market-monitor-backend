package dev.eolmae.marketmonitor.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SectorPriceSnapshotServiceTest {

    private static final LocalTime WINDOW_START = LocalTime.of(15, 30);
    private static final LocalTime WINDOW_END = LocalTime.of(15, 40);

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository =
            mock(SectorPriceSnapshotRepository.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService =
            new SectorPriceSnapshotService(sectorPriceSnapshotRepository);

    @Test
    void cleanupSnapshotsBefore_보존_대상을_넘겨_삭제_메서드를_호출한다() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 10, 0, 0);
        LocalDateTime kospiTime = LocalDateTime.of(2026, 8, 9, 15, 35);
        LocalDateTime kospiEarlier = LocalDateTime.of(2026, 8, 9, 15, 30);
        when(sectorPriceSnapshotRepository.findMarketSnapshotTimesInWindow(cutoff, WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        new MarketSnapshotTime(Market.KOSPI, kospiEarlier),
                        new MarketSnapshotTime(Market.KOSPI, kospiTime)));

        sectorPriceSnapshotService.cleanupSnapshotsBefore(cutoff);

        // 두 후보 중 늦은 시각(15:35)만 보존 대상으로 넘어간다.
        verify(sectorPriceSnapshotRepository)
                .deleteSnapshotsBefore(eq(cutoff), eq(List.of(new MarketSnapshotTime(Market.KOSPI, kospiTime))));
    }

    // 2단계(순수 자바 함수) — selectRetainedSnapshotTimes.

    @Test
    void selectRetainedSnapshotTimes_1530과_1535가_둘다_있으면_1535만_고른다() {
        LocalDateTime time1530 = LocalDateTime.of(2026, 8, 9, 15, 30);
        LocalDateTime time1535 = LocalDateTime.of(2026, 8, 9, 15, 35);
        List<MarketSnapshotTime> candidates =
                List.of(new MarketSnapshotTime(Market.KOSPI, time1530), new MarketSnapshotTime(Market.KOSPI, time1535));

        List<MarketSnapshotTime> retained =
                SectorPriceSnapshotService.selectRetainedSnapshotTimes(candidates, WINDOW_START, WINDOW_END);

        assertThat(retained).containsExactly(new MarketSnapshotTime(Market.KOSPI, time1535));
    }

    @Test
    void selectRetainedSnapshotTimes_1535가_없고_1530만_있으면_1530을_고른다() {
        LocalDateTime time1530 = LocalDateTime.of(2026, 8, 9, 15, 30);
        List<MarketSnapshotTime> candidates = List.of(new MarketSnapshotTime(Market.KOSPI, time1530));

        List<MarketSnapshotTime> retained =
                SectorPriceSnapshotService.selectRetainedSnapshotTimes(candidates, WINDOW_START, WINDOW_END);

        assertThat(retained).containsExactly(new MarketSnapshotTime(Market.KOSPI, time1530));
    }

    @Test
    void selectRetainedSnapshotTimes_윈도우가_통째로_비면_그_날짜는_결과에_없다() {
        List<MarketSnapshotTime> retained =
                SectorPriceSnapshotService.selectRetainedSnapshotTimes(List.of(), WINDOW_START, WINDOW_END);

        assertThat(retained).isEmpty();
    }

    @Test
    void selectRetainedSnapshotTimes_마켓별로_다른_시각이_잡힌다() {
        LocalDateTime kospiTime = LocalDateTime.of(2026, 8, 9, 15, 35);
        LocalDateTime kosdaqTime = LocalDateTime.of(2026, 8, 9, 15, 30);
        List<MarketSnapshotTime> candidates = List.of(
                new MarketSnapshotTime(Market.KOSPI, kospiTime), new MarketSnapshotTime(Market.KOSDAQ, kosdaqTime));

        List<MarketSnapshotTime> retained =
                SectorPriceSnapshotService.selectRetainedSnapshotTimes(candidates, WINDOW_START, WINDOW_END);

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

        List<MarketSnapshotTime> retained =
                SectorPriceSnapshotService.selectRetainedSnapshotTimes(candidates, WINDOW_START, WINDOW_END);

        assertThat(retained).isEmpty();
    }

    // 윈도우 상수가 뒤집히거나 SQL 술어가 틀어지면 보존 목록이 통째로 비고, 그대로 두면 삭제 술어가
    // "cutoff 이전 전부"로 무너진다. 이 레포엔 DB 테스트가 없어 그 고장이 빌드에서 안 걸리므로 서비스가
    // 스스로 막아야 한다.
    @Test
    void cleanupSnapshotsBefore_보존할_스냅샷이_하나도_없고_cutoff_이전에_행이_있으면_삭제하지_않고_예외를_던진다() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 10, 0, 0);
        when(sectorPriceSnapshotRepository.findMarketSnapshotTimesInWindow(cutoff, WINDOW_START, WINDOW_END))
                .thenReturn(List.of());
        when(sectorPriceSnapshotRepository.existsBefore(cutoff)).thenReturn(true);

        assertThatThrownBy(() -> sectorPriceSnapshotService.cleanupSnapshotsBefore(cutoff))
                .isInstanceOf(IllegalStateException.class);

        verify(sectorPriceSnapshotRepository, never()).deleteSnapshotsBefore(any(), any());
    }

    // cutoff 이전에 행이 아예 없으면 보존 목록이 비는 게 정상이라 막지 않는다.
    @Test
    void cleanupSnapshotsBefore_cutoff_이전에_행이_없으면_보존_목록이_비어도_그냥_진행한다() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 10, 0, 0);
        when(sectorPriceSnapshotRepository.findMarketSnapshotTimesInWindow(cutoff, WINDOW_START, WINDOW_END))
                .thenReturn(List.of());
        when(sectorPriceSnapshotRepository.existsBefore(cutoff)).thenReturn(false);

        sectorPriceSnapshotService.cleanupSnapshotsBefore(cutoff);

        verify(sectorPriceSnapshotRepository).deleteSnapshotsBefore(eq(cutoff), eq(List.of()));
    }
}
