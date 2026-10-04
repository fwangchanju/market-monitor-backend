package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClosingPriceReaderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 1);

    private final ClosingPriceCacheService cacheService = mock(ClosingPriceCacheService.class);
    private final ClosingPriceReader reader = new ClosingPriceReader(cacheService);

    private static ClosingPrices pricesOf(LocalDate date, String price) {
        return new ClosingPrices(date, Map.of("005930", new BigDecimal(price)));
    }

    @Test
    void 캐시의_기준일이_오늘이면_그대로_돌려주고_다시_적재하지_않는다() {
        ClosingPrices today = pricesOf(TODAY, "286500");
        when(cacheService.getCache()).thenReturn(today);

        ClosingPrices result = reader.closingPricesFor(TODAY, TODAY);

        assertThat(result).isSameAs(today);
        verify(cacheService, never()).evict();
    }

    @Test
    void 캐시의_기준일이_어제면_무효화하고_다시_적재한_값을_돌려준다() {
        ClosingPrices stale = pricesOf(YESTERDAY, "280000");
        ClosingPrices fresh = pricesOf(TODAY, "286500");
        when(cacheService.getCache()).thenReturn(stale).thenReturn(fresh);

        ClosingPrices result = reader.closingPricesFor(TODAY, TODAY);

        assertThat(result).isSameAs(fresh);
        verify(cacheService).evict();
    }

    @Test
    void 캐시의_기준일이_조회_날짜와_같으면_지난_날짜여도_캐시를_돌려준다() {
        // 주말: 어제(금요일) 종가가 캐시에 남아 있고, 금요일 스냅샷을 계산할 때다.
        ClosingPrices yesterday = pricesOf(YESTERDAY, "280000");
        when(cacheService.getCache()).thenReturn(yesterday);

        ClosingPrices result = reader.closingPricesFor(YESTERDAY, TODAY);

        assertThat(result).isSameAs(yesterday);
        verify(cacheService, never()).evict();
        verify(cacheService, never()).loadFor(YESTERDAY);
    }

    @Test
    void 캐시와_다른_지난_날짜는_직접_적재하고_기억해서_다시_적재하지_않는다() {
        ClosingPrices yesterday = pricesOf(YESTERDAY, "280000");
        when(cacheService.getCache()).thenReturn(new ClosingPrices(TODAY, Map.of()));
        when(cacheService.loadFor(YESTERDAY)).thenReturn(yesterday);

        ClosingPrices first = reader.closingPricesFor(YESTERDAY, TODAY);
        ClosingPrices second = reader.closingPricesFor(YESTERDAY, TODAY);

        assertThat(first).isSameAs(yesterday);
        assertThat(second).isSameAs(yesterday);
        verify(cacheService, times(1)).loadFor(YESTERDAY);
        verify(cacheService, never()).evict();
    }

    @Test
    void 기준가가_비어_있는_지난_날짜는_기억하지_않고_다음에_다시_적재한다() {
        ClosingPrices empty = new ClosingPrices(YESTERDAY, Map.of());
        when(cacheService.getCache()).thenReturn(new ClosingPrices(TODAY, Map.of()));
        when(cacheService.loadFor(YESTERDAY)).thenReturn(empty);

        reader.closingPricesFor(YESTERDAY, TODAY);
        reader.closingPricesFor(YESTERDAY, TODAY);

        verify(cacheService, times(2)).loadFor(YESTERDAY);
    }

    @Test
    void 기억하는_지난_날짜는_최근_세_날짜까지이고_가장_오래된_날짜부터_버린다() {
        LocalDate day1 = TODAY.minusDays(10);
        LocalDate day2 = TODAY.minusDays(9);
        LocalDate day3 = TODAY.minusDays(8);
        LocalDate day4 = TODAY.minusDays(7);
        when(cacheService.getCache()).thenReturn(new ClosingPrices(TODAY, Map.of()));
        for (LocalDate day : new LocalDate[] {day1, day2, day3, day4}) {
            when(cacheService.loadFor(day)).thenReturn(pricesOf(day, "100000"));
        }

        reader.closingPricesFor(day1, TODAY);
        reader.closingPricesFor(day2, TODAY);
        reader.closingPricesFor(day3, TODAY);
        reader.closingPricesFor(day4, TODAY);
        reader.closingPricesFor(day1, TODAY);

        verify(cacheService, times(2)).loadFor(day1);
        verify(cacheService, times(1)).loadFor(day4);
    }
}
