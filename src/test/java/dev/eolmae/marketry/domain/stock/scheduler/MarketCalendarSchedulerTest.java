package dev.eolmae.marketry.domain.stock.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.domain.stock.collector.MarketCalendarCollector;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketCalendarSchedulerTest {
    private final MarketCalendarCollector collector = mock(MarketCalendarCollector.class);
    private final MarketCalendarService service = mock(MarketCalendarService.class);
    private final MarketCalendarScheduler scheduler = new MarketCalendarScheduler(collector, service);
    private final LocalDate date = LocalDate.of(2026, 10, 9);

    @Test
    void 실패후재시도성공하면다음재시도는생략한다() {
        when(collector.collect(date)).thenReturn(false, true);
        scheduler.refresh(date);
        scheduler.retry(date);
        scheduler.retry(date);
        verify(collector, times(2)).collect(date);
    }

    @Test
    void 전날실패는다음날재시도하지않는다() {
        scheduler.refresh(date);
        scheduler.retry(date.plusDays(1));
        verify(collector, never()).collect(date.plusDays(1));
    }

    @Test
    void 시작시정상행이있으면그대로사용한다() {
        when(service.findByCountryAndDate(Country.KR, date))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, date, MarketCalendarStatus.HOLIDAY, null)));
        scheduler.initialize(date);
        verify(collector, never()).collect(date);
    }

    @Test
    void 시작시실패행이면조회하고최종실패까지정해진시각만재시도한다() {
        when(service.findByCountryAndDate(Country.KR, date))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, date, MarketCalendarStatus.FAILED, null)));
        scheduler.initialize(date);
        scheduler.retry(date);
        scheduler.retry(date);
        verify(collector, times(3)).collect(date);
    }
}
