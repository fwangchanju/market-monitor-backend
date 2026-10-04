package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClosingPriceCacheServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 2);
    private static final LocalDateTime WINDOW_START = DATE.atTime(15, 30);
    private static final LocalDateTime WINDOW_END = DATE.atTime(15, 40);

    private final SectorPriceSnapshotRepository repository = mock(SectorPriceSnapshotRepository.class);
    private final ClosingPriceCacheService service = new ClosingPriceCacheService(
            repository, new MarketHoursProperties(LocalTime.of(15, 30), LocalTime.of(15, 40)));

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
}
