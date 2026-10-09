package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SectorPriceSnapshotServiceTest {
    private static final LocalDate NORMAL = LocalDate.of(2025, 11, 12);
    private static final LocalDate LATE = LocalDate.of(2025, 11, 13);
    private final SectorPriceSnapshotRepository repository = mock(SectorPriceSnapshotRepository.class);
    private final MarketCalendarService calendarService = mock(MarketCalendarService.class);
    private final MarketCalendarTimeService timeService = mock(MarketCalendarTimeService.class);
    private final SectorPriceSnapshotService service =
            new SectorPriceSnapshotService(repository, calendarService, timeService);

    private static CalendarDayTimes times(LocalDate date, int hour) {
        return new CalendarDayTimes(
                false,
                date.atTime(8, 0),
                date.atTime(20, 0),
                date.atTime(hour, 30),
                date.atTime(hour, 30),
                date.atTime(hour, 40));
    }

    private static MarketSnapshotTime candidate(Market market, LocalDate date, int hour, int minute) {
        return new MarketSnapshotTime(market, date.atTime(hour, minute));
    }

    @Test
    void 날짜별_종가_구간과_마켓별_latest를_독립적으로_선정한다() {
        List<MarketSnapshotTime> candidates = List.of(
                candidate(Market.KOSPI, NORMAL, 15, 30), candidate(Market.KOSPI, NORMAL, 15, 35),
                candidate(Market.KOSDAQ, NORMAL, 15, 30), candidate(Market.KOSPI, LATE, 15, 35),
                candidate(Market.KOSPI, LATE, 16, 30), candidate(Market.KOSPI, LATE, 16, 35));
        assertThat(SectorPriceSnapshotService.selectRetainedSnapshotTimes(
                        candidates, Map.of(NORMAL, times(NORMAL, 15), LATE, times(LATE, 16))))
                .containsExactlyInAnyOrder(
                        candidate(Market.KOSPI, NORMAL, 15, 35),
                        candidate(Market.KOSDAQ, NORMAL, 15, 30),
                        candidate(Market.KOSPI, LATE, 16, 35));
    }

    @Test
    void 구간_시작은_포함하고_끝은_포함하지_않는다() {
        assertThat(SectorPriceSnapshotService.selectRetainedSnapshotTimes(
                        List.of(
                                candidate(Market.KOSPI, LATE, 16, 29),
                                candidate(Market.KOSPI, LATE, 16, 30),
                                candidate(Market.KOSPI, LATE, 16, 40)),
                        Map.of(LATE, times(LATE, 16))))
                .containsExactly(candidate(Market.KOSPI, LATE, 16, 30));
    }

    @Test
    void 휴장과_종가없는_그룹은_삭제목록에_포함하지_않는다() {
        assertThat(SectorPriceSnapshotService.selectRetainedSnapshotTimes(
                        List.of(candidate(Market.KOSPI, NORMAL, 15, 35), candidate(Market.KOSDAQ, LATE, 12, 0)),
                        Map.of(
                                NORMAL,
                                new CalendarDayTimes(true, null, null, null, null, null),
                                LATE,
                                times(LATE, 16))))
                .isEmpty();
    }

    @Test
    void 종가가_있는_그룹만_삭제대상으로_전달하고_날짜는_일괄조회한다() {
        LocalDateTime cutoff = LATE.plusDays(1).atTime(4, 0);
        when(repository.findMarketSnapshotTimesBefore(cutoff))
                .thenReturn(List.of(
                        candidate(Market.KOSPI, NORMAL, 15, 35), candidate(Market.KOSDAQ, NORMAL, 12, 0),
                        candidate(Market.KOSPI, LATE, 16, 35), candidate(Market.KOSDAQ, LATE, 15, 35)));
        when(calendarService.findByCountryAndDateIn(Country.KR, List.of(NORMAL, LATE)))
                .thenReturn(Map.of());
        when(timeService.resolve(NORMAL, null)).thenReturn(times(NORMAL, 15));
        when(timeService.resolve(LATE, null)).thenReturn(times(LATE, 16));
        service.cleanupSnapshotsBefore(cutoff);
        verify(calendarService).findByCountryAndDateIn(Country.KR, List.of(NORMAL, LATE));
        verify(calendarService, never()).findByCountryAndDate(any(), any());
        verify(repository)
                .deleteSnapshotsBefore(
                        eq(cutoff),
                        argThat(retained -> retained.size() == 2
                                && retained.contains(candidate(Market.KOSPI, NORMAL, 15, 35))
                                && retained.contains(candidate(Market.KOSPI, LATE, 16, 35))));
    }

    @Test
    void 전체_보존목록이_비면_삭제를_호출하지_않는다() {
        LocalDateTime cutoff = NORMAL.plusDays(1).atTime(4, 0);
        when(repository.findMarketSnapshotTimesBefore(cutoff))
                .thenReturn(List.of(candidate(Market.KOSDAQ, NORMAL, 12, 0)));
        when(calendarService.findByCountryAndDateIn(Country.KR, List.of(NORMAL)))
                .thenReturn(Map.of());
        when(timeService.resolve(NORMAL, null)).thenReturn(times(NORMAL, 15));
        service.cleanupSnapshotsBefore(cutoff);
        verify(repository, never()).deleteSnapshotsBefore(any(), any());
    }

    @Test
    void 시간표_일괄조회_실패는_기본_구간으로_정리한다() {
        LocalDateTime cutoff = NORMAL.plusDays(1).atTime(4, 0);
        when(repository.findMarketSnapshotTimesBefore(cutoff))
                .thenReturn(List.of(candidate(Market.KOSPI, NORMAL, 15, 35)));
        when(calendarService.findByCountryAndDateIn(Country.KR, List.of(NORMAL)))
                .thenThrow(new RuntimeException("DB failure"));
        when(timeService.resolve(NORMAL, null)).thenReturn(times(NORMAL, 15));
        service.cleanupSnapshotsBefore(cutoff);
        verify(repository).deleteSnapshotsBefore(cutoff, List.of(candidate(Market.KOSPI, NORMAL, 15, 35)));
    }

    @Test
    void 오래된_데이터가_없으면_시간표와_삭제도_조회하지_않는다() {
        LocalDateTime cutoff = NORMAL.atTime(4, 0);
        when(repository.findMarketSnapshotTimesBefore(cutoff)).thenReturn(List.of());
        service.cleanupSnapshotsBefore(cutoff);
        verify(calendarService, never()).findByCountryAndDateIn(any(), any());
        verify(repository, never()).deleteSnapshotsBefore(any(), any());
    }
}
