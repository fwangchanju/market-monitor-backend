package dev.eolmae.marketmonitor.domain.custom.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.custom.dto.StockSectorListItem;
import dev.eolmae.marketmonitor.domain.custom.service.CustomStockSectorService;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyCustomStockSectorControllerTest {

    private final CustomStockSectorService customStockSectorService = mock(CustomStockSectorService.class);
    private final LegacyCustomStockSectorController controller =
            new LegacyCustomStockSectorController(customStockSectorService);

    @Test
    void getStockCategories_섹터_미배정_종목은_제외하고_반환한다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 9, 25, 9, 0, 0);
        StockSectorListItem assigned = new StockSectorListItem(
                "005930", Market.KOSPI, "삼성전자", null, BigDecimal.TEN, "대형주", "전기전자", "반도체", "메모리", 11L);
        StockSectorListItem unassigned = new StockSectorListItem(
                "999999", Market.KOSPI, "신규상장", null, BigDecimal.ONE, "소형주", null, null, null, null);
        when(customStockSectorService.getStockCategories())
                .thenReturn(new SnapshotResponse<>(snapshotTime, List.of(assigned, unassigned)));

        SnapshotResponse<LegacyCustomStockSectorController.LegacyStockSectorListItem> response =
                controller.getStockCategories();

        assertThat(response.items())
                .extracting(LegacyCustomStockSectorController.LegacyStockSectorListItem::stockCode)
                .containsExactly("005930");
    }
}
