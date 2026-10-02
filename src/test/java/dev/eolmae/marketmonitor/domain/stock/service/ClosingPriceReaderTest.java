package dev.eolmae.marketmonitor.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        ClosingPrices result = reader.closingPricesFor(TODAY);

        assertThat(result).isSameAs(today);
        verify(cacheService, never()).evict();
    }

    @Test
    void 캐시의_기준일이_어제면_무효화하고_다시_적재한_값을_돌려준다() {
        ClosingPrices stale = pricesOf(YESTERDAY, "280000");
        ClosingPrices fresh = pricesOf(TODAY, "286500");
        when(cacheService.getCache()).thenReturn(stale).thenReturn(fresh);

        ClosingPrices result = reader.closingPricesFor(TODAY);

        assertThat(result).isSameAs(fresh);
        verify(cacheService).evict();
    }
}
