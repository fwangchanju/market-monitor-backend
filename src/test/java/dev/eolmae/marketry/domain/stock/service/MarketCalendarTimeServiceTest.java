package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.domain.stock.entity.IntegratedPeriod;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingPeriod;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MarketCalendarTimeServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 2);
    private final MarketCalendarService calendarService = Mockito.mock(MarketCalendarService.class);
    private final MarketCalendarTimeService service = new MarketCalendarTimeService(
            calendarService, new MarketHoursProperties(LocalTime.of(15, 30), LocalTime.of(15, 40)));

    @BeforeEach
    void 기본_수집시간() {
        ReflectionTestUtils.setField(service, "startHour", 8);
        ReflectionTestUtils.setField(service, "endHour", 20);
    }

    @Test
    void 정상날은_전체_세션_최소시작과_최대종료를_쓴다() {
        CalendarDayTimes times = service.resolve(
                DATE,
                trading(new IntegratedPeriod(
                        period(8, 0, 8, 50, null), period(9, 0, 15, 30, null), period(15, 30, 20, 0, time(15, 40)))));

        assertThat(times.collectionStart()).isEqualTo(DATE.atTime(8, 0));
        assertThat(times.collectionEnd()).isEqualTo(DATE.atTime(20, 0));
        assertThat(times.closingWindowStart()).isEqualTo(DATE.atTime(15, 30));
        assertThat(times.closingWindowEnd()).isEqualTo(DATE.atTime(15, 40));
    }

    @Test
    void 프리마켓이_없으면_10시_개장과_16시30분_종가구간을_적용한다() {
        CalendarDayTimes times = service.resolve(
                DATE,
                trading(new IntegratedPeriod(null, period(10, 0, 16, 30, null), period(16, 30, 20, 0, time(16, 40)))));

        assertThat(times.collectionStart()).isEqualTo(DATE.atTime(10, 0));
        assertThat(times.collectionEnd()).isEqualTo(DATE.atTime(20, 0));
        assertThat(times.regularMarketEnd()).isEqualTo(DATE.atTime(16, 30));
        assertThat(times.closingWindowStart()).isEqualTo(DATE.atTime(16, 30));
        assertThat(times.closingWindowEnd()).isEqualTo(DATE.atTime(16, 40));
    }

    @Test
    void 수능날에도_프리마켓없이_10시부터_수집한다() {
        LocalDate examDate = LocalDate.of(2025, 11, 13);
        IntegratedPeriod integrated = new IntegratedPeriod(
                null,
                new TradingPeriod(
                        examDate.atTime(10, 0).atOffset(ZoneOffset.ofHours(9)),
                        null,
                        null,
                        examDate.atTime(16, 30).atOffset(ZoneOffset.ofHours(9))),
                new TradingPeriod(
                        examDate.atTime(16, 30).atOffset(ZoneOffset.ofHours(9)),
                        null,
                        examDate.atTime(16, 40).atOffset(ZoneOffset.ofHours(9)),
                        examDate.atTime(20, 0).atOffset(ZoneOffset.ofHours(9))));

        CalendarDayTimes times = service.resolve(
                examDate, MarketCalendar.create(Country.KR, examDate, MarketCalendarStatus.TRADING_DAY, integrated));

        assertThat(times.collectionStart()).isEqualTo(examDate.atTime(10, 0));
        assertThat(times.closingWindowEnd()).isEqualTo(examDate.atTime(16, 40));
    }

    @Test
    void 휴장은_시간값없이_휴장으로_반환한다() {
        CalendarDayTimes times =
                service.resolve(DATE, MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.HOLIDAY, null));

        assertThat(times.holiday()).isTrue();
        assertThat(times.collectionStart()).isNull();
    }

    @Test
    void 세션과_단일가경계가_없으면_종가만_기본시간으로_돌린다() {
        CalendarDayTimes times =
                service.resolve(DATE, trading(new IntegratedPeriod(null, period(10, 0, 16, 30, null), null)));

        assertThat(times.collectionStart()).isEqualTo(DATE.atTime(10, 0));
        assertThat(times.collectionEnd()).isEqualTo(DATE.atTime(16, 30));
        assertThat(times.regularMarketEnd()).isEqualTo(DATE.atTime(16, 30));
        assertThat(times.closingWindowStart()).isEqualTo(DATE.atTime(15, 30));
        assertThat(times.closingWindowEnd()).isEqualTo(DATE.atTime(15, 40));
    }

    @Test
    void 애프터마켓_단일가끝이_없어도_수집시간은_세션을_쓴다() {
        CalendarDayTimes times = service.resolve(
                DATE, trading(new IntegratedPeriod(null, period(10, 0, 16, 30, null), period(16, 30, 20, 0, null))));

        assertThat(times.collectionEnd()).isEqualTo(DATE.atTime(20, 0));
        assertThat(times.closingWindowStart()).isEqualTo(DATE.atTime(15, 30));
    }

    @Test
    void 정규장이_없으면_정규종료와_종가만_기본값이다() {
        CalendarDayTimes times = service.resolve(
                DATE,
                trading(new IntegratedPeriod(period(8, 0, 8, 50, null), null, period(16, 30, 20, 0, time(16, 40)))));

        assertThat(times.collectionStart()).isEqualTo(DATE.atTime(8, 0));
        assertThat(times.regularMarketEnd()).isEqualTo(DATE.atTime(15, 30));
        assertThat(times.closingWindowEnd()).isEqualTo(DATE.atTime(15, 40));
    }

    @Test
    void 시간대가_UTC여도_국내시각으로_변환한다() {
        TradingPeriod session = new TradingPeriod(
                time(10, 0).withOffsetSameInstant(ZoneOffset.UTC),
                null,
                null,
                time(16, 30).withOffsetSameInstant(ZoneOffset.UTC));

        CalendarDayTimes times = service.resolve(DATE, trading(new IntegratedPeriod(null, session, null)));

        assertThat(times.collectionStart()).isEqualTo(DATE.atTime(10, 0));
        assertThat(times.regularMarketEnd()).isEqualTo(DATE.atTime(16, 30));
    }

    @Test
    void 행이_없으면_기본시간으로_수집한다() {
        when(calendarService.findByCountryAndDate(Country.KR, DATE)).thenReturn(Optional.empty());

        assertFallback(service.resolve(DATE));
    }

    @Test
    void FAILED이면_기본시간으로_수집한다() {
        assertFallback(
                service.resolve(DATE, MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.FAILED, null)));
    }

    @Test
    void DB조회오류는_수집을_막지_않는다() {
        when(calendarService.findByCountryAndDate(Country.KR, DATE)).thenThrow(new RuntimeException("DB unavailable"));

        assertFallback(service.resolve(DATE));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void 시간표없는_주말은_수집만_생략하고_종가_기본시간은_유지한다(int day) {
        LocalDate weekend = LocalDate.of(2026, 1, day);
        when(calendarService.findByCountryAndDate(Country.KR, weekend)).thenReturn(Optional.empty());

        assertThat(service.resolveForCollection(weekend).holiday()).isTrue();
        CalendarDayTimes closing = service.resolve(weekend);
        assertThat(closing.holiday()).isFalse();
        assertThat(closing.closingWindowStart()).isEqualTo(weekend.atTime(15, 30));
        assertThat(closing.closingWindowEnd()).isEqualTo(weekend.atTime(15, 40));
    }

    @Test
    void 시간표없는_평일은_기본시간으로_수집한다() {
        when(calendarService.findByCountryAndDate(Country.KR, DATE)).thenReturn(Optional.empty());

        assertFallback(service.resolveForCollection(DATE));
    }

    @Test
    void 주말도_FAILED_레코드가_있으면_기존_실패_정책을_따른다() {
        LocalDate weekend = LocalDate.of(2026, 1, 3);
        when(calendarService.findByCountryAndDate(Country.KR, weekend))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, weekend, MarketCalendarStatus.FAILED, null)));

        CalendarDayTimes times = service.resolveForCollection(weekend);
        assertThat(times.holiday()).isFalse();
        assertThat(times.collectionStart()).isEqualTo(weekend.atTime(8, 0));
        assertThat(times.collectionEnd()).isEqualTo(weekend.atTime(20, 0));
    }

    @Test
    void 주말의_거래일_시간표가_있으면_요일보다_시간표를_우선한다() {
        LocalDate weekend = LocalDate.of(2026, 1, 3);
        TradingPeriod regular = new TradingPeriod(
                weekend.atTime(10, 0).atOffset(ZoneOffset.ofHours(9)),
                null,
                null,
                weekend.atTime(15, 30).atOffset(ZoneOffset.ofHours(9)));
        when(calendarService.findByCountryAndDate(Country.KR, weekend))
                .thenReturn(Optional.of(MarketCalendar.create(
                        Country.KR,
                        weekend,
                        MarketCalendarStatus.TRADING_DAY,
                        new IntegratedPeriod(null, regular, null))));

        CalendarDayTimes times = service.resolveForCollection(weekend);
        assertThat(times.holiday()).isFalse();
        assertThat(times.collectionStart()).isEqualTo(weekend.atTime(10, 0));
        assertThat(times.collectionEnd()).isEqualTo(weekend.atTime(15, 30));
    }

    @Test
    void DB오류를_날짜_레코드_없음으로_단정해_주말_수집을_막지_않는다() {
        LocalDate weekend = LocalDate.of(2026, 1, 3);
        when(calendarService.findByCountryAndDate(Country.KR, weekend))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThat(service.resolveForCollection(weekend).holiday()).isFalse();
    }

    @Test
    void 수동적재한_휴장_시간표를_다음_수집_판정에_반영한다() {
        when(calendarService.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.HOLIDAY, null)));

        assertThat(service.resolveForCollection(DATE).holiday()).isFalse();
        assertThat(service.resolveForCollection(DATE).holiday()).isTrue();
    }

    private void assertFallback(CalendarDayTimes times) {
        assertThat(times.holiday()).isFalse();
        assertThat(times.collectionStart()).isEqualTo(DATE.atTime(8, 0));
        assertThat(times.collectionEnd()).isEqualTo(DATE.atTime(20, 0));
        assertThat(times.regularMarketEnd()).isEqualTo(DATE.atTime(15, 30));
        assertThat(times.closingWindowEnd()).isEqualTo(DATE.atTime(15, 40));
    }

    private MarketCalendar trading(IntegratedPeriod integrated) {
        return MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.TRADING_DAY, integrated);
    }

    private TradingPeriod period(
            int startHour, int startMinute, int endHour, int endMinute, OffsetDateTime auctionEnd) {
        return new TradingPeriod(time(startHour, startMinute), null, auctionEnd, time(endHour, endMinute));
    }

    private OffsetDateTime time(int hour, int minute) {
        return DATE.atTime(hour, minute).atOffset(ZoneOffset.ofHours(9));
    }
}
