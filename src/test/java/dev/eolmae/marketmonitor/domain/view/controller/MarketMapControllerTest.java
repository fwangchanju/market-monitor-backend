package dev.eolmae.marketmonitor.domain.view.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.eolmae.marketmonitor.domain.custom.service.CustomScaleService;
import dev.eolmae.marketmonitor.domain.custom.service.CustomSectorService;
import dev.eolmae.marketmonitor.domain.custom.service.CustomValueTierThresholdService;
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
                    mock(CustomSectorService.class),
                    mock(CustomScaleService.class),
                    mock(CustomValueTierThresholdService.class)))
            .build();

    @Test
    void getMarketMap_snapshotTime이_LocalDateTime으로_바인딩된다() throws Exception {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 9, 22, 10, 5, 0);
        when(marketMapQueryService.getCustomMarketMap(MarketQuery.KOSPI, snapshotTime, false))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=true&snapshotTime=2026-09-22T10:05:00"));

        verify(marketMapQueryService).getCustomMarketMap(MarketQuery.KOSPI, snapshotTime, false);
    }

    @Test
    void getMarketMap_nxtOnly가_내_분류에도_전달된다() throws Exception {
        when(marketMapQueryService.getCustomMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(true)))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=true&nxtOnly=true"));

        verify(marketMapQueryService).getCustomMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(true));
    }

    @Test
    void getMarketMap_snapshotTime이_없으면_null로_바인딩된다() throws Exception {
        when(marketMapQueryService.getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(false)))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=false"));

        verify(marketMapQueryService).getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(false));
    }

    @Test
    void getMarketMap_nxtOnly가_거래소_분류에_전달된다() throws Exception {
        when(marketMapQueryService.getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(true)))
                .thenReturn(MarketMapResponse.empty());

        mockMvc.perform(get("/api/map?market=KOSPI&isCustom=false&nxtOnly=true"));

        verify(marketMapQueryService).getDefaultMarketMap(eq(MarketQuery.KOSPI), isNull(), eq(true));
    }
}
