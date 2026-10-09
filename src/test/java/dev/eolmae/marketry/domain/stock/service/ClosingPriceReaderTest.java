package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClosingPriceReaderTest {
    @Test
    void 조회하는_스냅샷_날짜로_기준가를_읽는다() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        LocalDate snapshotDate = today.minusDays(1);
        ClosingPriceCacheService cache = mock(ClosingPriceCacheService.class);
        ClosingPrices prices = new ClosingPrices(snapshotDate, Map.of("005930", BigDecimal.TEN));
        when(cache.getFor(eq(snapshotDate), any())).thenReturn(prices);
        assertThat(new ClosingPriceReader(cache).closingPricesFor(snapshotDate, today))
                .isEqualTo(prices);
    }
}
