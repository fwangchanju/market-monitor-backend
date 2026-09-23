package dev.eolmae.marketmonitor.domain.view.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapScaleService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.stock.service.MarketMapExcludedStockService;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// 이 레포엔 컨트롤러 테스트도 @WebMvcTest 의존성도 없다(테스트 기준 문서 참고) — 여기서만 standaloneSetup으로
// snapshotTime 바인딩만 좁게 확인한다. @WebMvcTest 등 새 의존성은 더하지 않는다.
class MarketMapControllerTest {

    private final MarketMapQueryService marketMapQueryService = mock(MarketMapQueryService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MarketMapController(
                    marketMapQueryService,
                    mock(MarketMapExcludedStockService.class),
                    mock(MarketMapCategoryService.class),
                    mock(MarketMapScaleService.class),
                    mock(MarketValueTierThresholdService.class)))
            .build();

    @Test
    void getMarketMap_snapshotTime이_LocalDateTime으로_바인딩된다() throws Exception {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 9, 22, 10, 5, 0);
        when(marketMapQueryService.getCustomMarketMap(MarketQuery.KOSPI, snapshotTime))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=true&snapshotTime=2026-09-22T10:05:00"));

        verify(marketMapQueryService).getCustomMarketMap(MarketQuery.KOSPI, snapshotTime);
    }

    @Test
    void getMarketMap_snapshotTime이_없으면_null로_바인딩된다() throws Exception {
        when(marketMapQueryService.getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull()))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=false"));

        verify(marketMapQueryService).getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull());
    }
}
