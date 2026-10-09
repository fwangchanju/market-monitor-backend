package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.querydsl.jpa.JPQLTemplates;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.IntegratedSessions;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingSession;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SectorPriceSnapshotServiceTest {
    private static final LocalDate DATE = LocalDate.of(2025, 11, 13);
    private final SectorPriceSnapshotRepository repository = mock(SectorPriceSnapshotRepository.class);
    private final MarketCalendarService calendarService = mock(MarketCalendarService.class);
    private final MarketCalendarTimeService timeService = new MarketCalendarTimeService(
            calendarService, new MarketHoursProperties(LocalTime.of(15, 30), LocalTime.of(15, 40)));
    private final SectorPriceSnapshotService service = new SectorPriceSnapshotService(repository, timeService);

    @BeforeEach
    void 기본_수집시간() {
        ReflectionTestUtils.setField(timeService, "startHour", 8);
        ReflectionTestUtils.setField(timeService, "endHour", 20);
    }

    private static MarketSnapshotTime retained(Market market, int hour, int minute) {
        return new MarketSnapshotTime(market, DATE.atTime(hour, minute));
    }

    private void trading(int closingHour) {
        ZoneOffset offset = ZoneOffset.ofHours(9);
        IntegratedSessions integrated = new IntegratedSessions(
                null,
                new TradingSession(
                        DATE.atTime(10, 0).atOffset(offset),
                        null,
                        null,
                        DATE.atTime(closingHour, 30).atOffset(offset)),
                new TradingSession(
                        DATE.atTime(closingHour, 30).atOffset(offset),
                        null,
                        DATE.atTime(closingHour, 40).atOffset(offset),
                        DATE.atTime(20, 0).atOffset(offset)));
        when(calendarService.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(Optional.of(
                        MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.TRADING_DAY, integrated)));
    }

    @Test
    void 평소_종가구간의_시장별_보존시각으로_지정_날짜만_정리한다() {
        trading(15);
        List<MarketSnapshotTime> retained = List.of(retained(Market.KOSPI, 15, 35), retained(Market.KOSDAQ, 15, 30));
        when(repository.findLatestMarketSnapshotTimesBetween(DATE.atTime(15, 30), DATE.atTime(15, 40)))
                .thenReturn(retained);

        service.cleanupSnapshotsForDate(DATE);

        verify(repository).deleteSnapshotsForDate(DATE, retained);
    }

    @Test
    void 수능날은_16시30분부터_16시40분_미만의_latest를_보존한다() {
        trading(16);
        List<MarketSnapshotTime> retained = List.of(retained(Market.KOSPI, 16, 35), retained(Market.KOSDAQ, 16, 35));
        when(repository.findLatestMarketSnapshotTimesBetween(DATE.atTime(16, 30), DATE.atTime(16, 40)))
                .thenReturn(retained);

        service.cleanupSnapshotsForDate(DATE);

        verify(repository).deleteSnapshotsForDate(DATE, retained);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = MarketCalendarStatus.class, names = "FAILED")
    void 실패나_시간표_없음은_기본_종가구간으로_삭제한다(MarketCalendarStatus status) {
        when(calendarService.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(
                        status == null
                                ? Optional.empty()
                                : Optional.of(MarketCalendar.create(Country.KR, DATE, status, null)));
        List<MarketSnapshotTime> retained = List.of(retained(Market.KOSPI, 15, 35));
        when(repository.findLatestMarketSnapshotTimesBetween(DATE.atTime(15, 30), DATE.atTime(15, 40)))
                .thenReturn(retained);

        service.cleanupSnapshotsForDate(DATE);

        verify(repository).deleteSnapshotsForDate(DATE, retained);
    }

    @Test
    void 휴장일은_종가조회와_삭제_전에_생략한다() {
        when(calendarService.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.HOLIDAY, null)));

        service.cleanupSnapshotsForDate(DATE);

        verifyNoInteractions(repository);
    }

    @Test
    void 종가_후보가_없는_날짜는_삭제하지_않는다() {
        trading(15);
        when(repository.findLatestMarketSnapshotTimesBetween(DATE.atTime(15, 30), DATE.atTime(15, 40)))
                .thenReturn(List.of());

        service.cleanupSnapshotsForDate(DATE);

        verify(repository, never()).deleteSnapshotsForDate(any(), any());
    }

    @Test
    void 종가가_있는_시장만_삭제대상으로_전달한다() {
        trading(15);
        List<MarketSnapshotTime> retained = List.of(retained(Market.KOSPI, 15, 35));
        when(repository.findLatestMarketSnapshotTimesBetween(DATE.atTime(15, 30), DATE.atTime(15, 40)))
                .thenReturn(retained);

        service.cleanupSnapshotsForDate(DATE);

        verify(repository).deleteSnapshotsForDate(DATE, retained);
    }

    @Test
    void 시간표_DB조회가_실패하면_기본_종가구간으로_정리한다() {
        when(calendarService.findByCountryAndDate(Country.KR, DATE)).thenThrow(new RuntimeException("DB failure"));
        List<MarketSnapshotTime> retained = List.of(retained(Market.KOSPI, 15, 35));
        when(repository.findLatestMarketSnapshotTimesBetween(DATE.atTime(15, 30), DATE.atTime(15, 40)))
                .thenReturn(retained);

        service.cleanupSnapshotsForDate(DATE);

        verify(repository).deleteSnapshotsForDate(DATE, retained);
    }

    @Test
    void 삭제쿼리는_대상_하루로_범위를_제한하고_보존시각의_모든_종목을_제외한다() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(12);
        var retentionRepository =
                new SectorPriceSnapshotRepositoryImpl(new JPAQueryFactory(JPQLTemplates.DEFAULT, entityManager));

        long deleted = retentionRepository.deleteSnapshotsForDate(DATE, List.of(retained(Market.KOSPI, 15, 35)));

        assertThat(deleted).isEqualTo(12);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(queryCaptor.capture());
        assertThat(queryCaptor.getValue())
                .contains("snapshotTime >=", "snapshotTime <", "marketType =", "snapshotTime <>");
        assertThat(queryCaptor.getValue()).doesNotContain("stockCode", "exchangeType");
        verify(query).setParameter(1, DATE.atStartOfDay());
        verify(query).setParameter(2, DATE.plusDays(1).atStartOfDay());
        verify(query).setParameter(3, Market.KOSPI);
        verify(query).setParameter(4, DATE.atTime(15, 35));
    }

    @Test
    void 보존시각이_없으면_삭제쿼리를_실행하지_않는다() {
        EntityManager entityManager = mock(EntityManager.class);
        var retentionRepository =
                new SectorPriceSnapshotRepositoryImpl(new JPAQueryFactory(JPQLTemplates.DEFAULT, entityManager));

        assertThat(retentionRepository.deleteSnapshotsForDate(DATE, List.of())).isZero();
        verifyNoInteractions(entityManager);
    }
}
