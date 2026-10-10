package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.IntegratedSessions;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingSession;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.SnapshotDaySummary;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.TimeWindow;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class SectorPriceSnapshotServiceTest {
    private static final LocalDate DATE = LocalDate.of(2025, 11, 13);
    private final SectorPriceSnapshotRepository repository = mock(SectorPriceSnapshotRepository.class);
    private final MarketCalendarService calendarService = mock(MarketCalendarService.class);
    private final MarketCalendarTimeService timeService = new MarketCalendarTimeService(
            calendarService, new MarketHoursProperties(LocalTime.of(15, 30), LocalTime.of(15, 40)));
    private final SectorPriceSnapshotService service = new SectorPriceSnapshotService(repository, timeService);

    private void calendar(int hour) {
        var offset = ZoneOffset.ofHours(9);
        var integrated = new IntegratedSessions(
                null,
                new TradingSession(
                        DATE.atTime(10, 0).atOffset(offset),
                        null,
                        null,
                        DATE.atTime(hour, 30).atOffset(offset)),
                new TradingSession(
                        DATE.atTime(hour, 30).atOffset(offset),
                        null,
                        DATE.atTime(hour, 40).atOffset(offset),
                        DATE.atTime(20, 0).atOffset(offset)));
        when(calendarService.findByCountryAndDateIn(eq(Country.KR), any()))
                .thenReturn(Map.of(
                        DATE, MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.TRADING_DAY, integrated)));
    }

    @Test
    void 한_시각만_있으면_새_시간표_경계_밖이라도_기존_값을_쓴다() {
        calendar(16);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(new SnapshotDaySummary(Market.KOSPI, DATE.atTime(17, 0), 1, null)));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(17, 0));
        service.cleanupSnapshotsForDate(DATE);
        verify(repository, never()).deleteSnapshotsForDate(any(), any());
    }

    @Test
    void 여러_시각이면_하한_없이_시간외_이전_latest를_조회하고_정리에_같이_쓴다() {
        calendar(16);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(new SnapshotDaySummary(Market.KOSPI, DATE.atTime(20, 0), 3, DATE.atTime(15, 35))));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 35));
        ArgumentCaptor<List<TimeWindow>> windows = ArgumentCaptor.captor();
        verify(repository).findSnapshotDaySummaries(windows.capture());
        assertThat(windows.getValue()).containsExactly(new TimeWindow(DATE.atStartOfDay(), DATE.atTime(16, 40)));
        service.cleanupSnapshotsForDate(DATE);
        verify(repository)
                .deleteSnapshotsForDate(DATE, List.of(new MarketSnapshotTime(Market.KOSPI, DATE.atTime(15, 35))));
    }

    @Test
    void 후보가_없는_날은_전날로_대체하지_않고_삭제도_보류한다() {
        calendar(15);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(new SnapshotDaySummary(Market.KOSPI, DATE.atTime(20, 0), 2, null)));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .isEmpty();
        service.cleanupSnapshotsForDate(DATE);
        verify(repository, never()).deleteSnapshotsForDate(any(), any());
    }

    @Test
    void 통합_달력은_두_시장에_공통_시각이_있는_날만_활성화한다() {
        calendar(15);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(
                        new SnapshotDaySummary(Market.KOSPI, DATE.atTime(15, 35), 1, null),
                        new SnapshotDaySummary(Market.KOSDAQ, DATE.atTime(15, 30), 1, null)));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI, Market.KOSDAQ), YearMonth.from(DATE), DATE))
                .isEmpty();
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 35));
    }

    @ParameterizedTest
    @EnumSource(
            value = MarketCalendarStatus.class,
            names = {"FAILED", "HOLIDAY"})
    void 실패와_휴장은_달력과_정리에서_제외한다(MarketCalendarStatus status) {
        when(calendarService.findByCountryAndDateIn(eq(Country.KR), any()))
                .thenReturn(Map.of(DATE, MarketCalendar.create(Country.KR, DATE, status, null)));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .isEmpty();
        service.cleanupSnapshotsForDate(DATE);
        verifyNoInteractions(repository);
    }

    @Test
    void 시간표_없는_날도_달력과_정리에서_제외한다() {
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .isEmpty();
        service.cleanupSnapshotsForDate(DATE);
        verifyNoInteractions(repository);
    }

    @Test
    void 토스가_미리_준_미래_날짜는_달력에서_제외한다() {
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE.minusDays(1)))
                .isEmpty();
        ArgumentCaptor<Collection<LocalDate>> dates = ArgumentCaptor.captor();
        verify(calendarService).findByCountryAndDateIn(eq(Country.KR), dates.capture());
        assertThat(dates.getValue()).doesNotContain(DATE);
        assertThat(dates.getValue()).allMatch(date -> date.isBefore(DATE));
    }

    @Test
    void 최근_세_거래일은_시각_개수와_관계없이_센_뒤_이전의_미정리_날짜만_선택한다() {
        List<LocalDate> dates =
                List.of(DATE, DATE.minusDays(1), DATE.minusDays(2), DATE.minusDays(3), DATE.minusDays(6));
        when(repository.findTradingSnapshotDates(DATE)).thenReturn(dates);
        when(repository.findMultipleSnapshotDates(dates.get(3))).thenReturn(List.of(dates.get(4), dates.get(3)));
        assertThat(service.findCleanupDates(DATE, 3)).containsExactly(dates.get(4), dates.get(3));
    }

    @Test
    void 가격_있는_거래일이_세개_이하면_정리하지_않는다() {
        when(repository.findTradingSnapshotDates(DATE)).thenReturn(List.of(DATE, DATE.minusDays(1)));
        assertThat(service.findCleanupDates(DATE, 3)).isEmpty();
        verify(repository, never()).findMultipleSnapshotDates(any());
    }
}
