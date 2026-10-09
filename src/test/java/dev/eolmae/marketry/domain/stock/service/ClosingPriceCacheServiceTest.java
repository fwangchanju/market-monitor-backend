package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.event.MarketCalendarChangedEvent;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClosingPriceCacheServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 2);
    private static final LocalDateTime WINDOW_START = DATE.atTime(15, 30);
    private static final LocalDateTime WINDOW_END = DATE.atTime(15, 40);

    private final SectorPriceSnapshotRepository repository = mock(SectorPriceSnapshotRepository.class);
    private final MarketCalendarTimeService timeService = mock(MarketCalendarTimeService.class);
    private final ClosingPriceCacheService service = new ClosingPriceCacheService(repository, timeService);

    @BeforeEach
    void 기본_종가_구간을_설정한다() {
        when(timeService.resolve(DATE)).thenReturn(dayTimes(DATE, 15));
    }

    private static CalendarDayTimes dayTimes(LocalDate date, int closingHour) {
        return new CalendarDayTimes(
                false,
                date.atTime(8, 0),
                date.atTime(20, 0),
                date.atTime(closingHour, 30),
                date.atTime(closingHour, 30),
                date.atTime(closingHour, 40));
    }

    private void stubLatest(LocalDate date, int hour, String price) {
        LocalDateTime latest = date.atTime(hour, 35);
        SectorPriceSnapshot snapshot = snapshot("005930", latest, price);
        when(repository.findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
                        date.atTime(hour, 30), date.atTime(hour, 40)))
                .thenReturn(Optional.of(snapshot));
        when(repository.findByMarketTypeInAndSnapshotTime(any(), eq(latest))).thenReturn(List.of(snapshot));
    }

    private static SectorPriceSnapshot snapshot(String stockCode, LocalDateTime time, String price) {
        return SectorPriceSnapshot.create(
                Market.KOSPI,
                time,
                stockCode,
                ExchangeType.SOR,
                stockCode,
                new BigDecimal(price),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    @Test
    void 종가_윈도우의_가장_늦은_스냅샷_시각의_가격을_종목별로_담는다() {
        LocalDateTime latest = DATE.atTime(15, 35);
        when(repository.findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
                        WINDOW_START, WINDOW_END))
                .thenReturn(Optional.of(snapshot("005930", latest, "286500")));
        when(repository.findByMarketTypeInAndSnapshotTime(any(), eq(latest)))
                .thenReturn(List.of(snapshot("005930", latest, "286500"), snapshot("000660", latest, "980000")));

        ClosingPrices result = service.loadFor(DATE);

        assertThat(result.baseDate()).isEqualTo(DATE);
        assertThat(result.priceByStockCode())
                .containsEntry("005930", new BigDecimal("286500"))
                .containsEntry("000660", new BigDecimal("980000"))
                .hasSize(2);
    }

    @Test
    void 윈도우_경계를_baseDate의_설정_시각으로_조회한다() {
        when(repository.findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
                        any(), any()))
                .thenReturn(Optional.empty());

        service.loadFor(DATE);

        // 하한은 포함(15:30), 상한은 미포함(15:40) — 16:00 같은 시간외 체결가가 종가로 잡히지 않는다.
        verify(repository)
                .findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
                        WINDOW_START, WINDOW_END);
    }

    @Test
    void 윈도우에_스냅샷이_없는_날은_기준가가_빈_채로_그날로_표시된다() {
        when(repository.findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
                        any(), any()))
                .thenReturn(Optional.empty());

        ClosingPrices result = service.loadFor(DATE);

        assertThat(result.baseDate()).isEqualTo(DATE);
        assertThat(result.priceByStockCode()).isEmpty();
        verify(repository, never()).findByMarketTypeInAndSnapshotTime(any(), any());
    }

    @Test
    void 진행_중_종가_구간은_새로운_latest를_다시_읽는다() {
        SectorPriceSnapshot early = snapshot("005930", DATE.atTime(15, 30), "100");
        when(repository.findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
                        WINDOW_START, WINDOW_END))
                .thenReturn(Optional.of(early));
        when(repository.findByMarketTypeInAndSnapshotTime(any(), eq(DATE.atTime(15, 30))))
                .thenReturn(List.of(early));
        assertThat(service.getFor(DATE, DATE.atTime(15, 31)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("100"));
        stubLatest(DATE, 15, "110");
        assertThat(service.getFor(DATE, DATE.atTime(15, 36)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("110"));
    }

    @Test
    void 끝난_구간은_캐시하고_시간표_변경_후_현재와_과거_캐시를_갱신한다() {
        LocalDate past = DATE.minusDays(1);
        when(timeService.resolve(past)).thenReturn(dayTimes(past, 15));
        stubLatest(DATE, 15, "100");
        stubLatest(past, 15, "200");
        service.getFor(DATE, DATE.atTime(18, 0));
        service.getFor(past, DATE.atTime(18, 0));
        stubLatest(DATE, 15, "110");
        stubLatest(past, 15, "210");
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("100"));
        service.onMarketCalendarChanged(new MarketCalendarChangedEvent(Country.KR, past));
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("110"));
        assertThat(service.getFor(past, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("210"));
    }

    @Test
    void 기본_구간_캐시도_시간표가_복구되면_늦어진_구간으로_다시_계산한다() {
        stubLatest(DATE, 15, "100");
        service.getFor(DATE, DATE.atTime(18, 0));
        when(timeService.resolve(DATE)).thenReturn(dayTimes(DATE, 16));
        stubLatest(DATE, 16, "150");
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("150"));
    }

    @Test
    void 빈_결과는_캐시하지_않아_나중에_저장된_종가를_읽는다() {
        when(repository.findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
                        WINDOW_START, WINDOW_END))
                .thenReturn(Optional.empty());
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode()).isEmpty();
        stubLatest(DATE, 15, "100");
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("100"));
    }

    @Test
    void 휴장일은_종가가_없고_DB_가격을_조회하지_않는다() {
        when(timeService.resolve(DATE)).thenReturn(new CalendarDayTimes(true, null, null, null, null, null));
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode()).isEmpty();
        verify(repository, never())
                .findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(any(), any());
    }

    @Test
    void 종가_끝_경계부터_안정된_값을_캐시한다() {
        stubLatest(DATE, 15, "100");
        service.getFor(DATE, WINDOW_END);
        service.getFor(DATE, WINDOW_END.plusMinutes(1));
        verify(repository, org.mockito.Mockito.times(1))
                .findByMarketTypeInAndSnapshotTime(any(), eq(DATE.atTime(15, 35)));
    }
}
