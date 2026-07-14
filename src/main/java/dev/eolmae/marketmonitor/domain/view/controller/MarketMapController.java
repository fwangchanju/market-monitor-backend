package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryGroup;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MarketMapController {

    private final MarketMapQueryService marketMapQueryService;

    @GetMapping("/market-map")
    public SnapshotResponse<MarketMapCategoryGroup> getMarketMap(
            @RequestParam Market market, @RequestParam boolean isExclude) {
        return marketMapQueryService.getMarketMap(market, isExclude);
    }
}
