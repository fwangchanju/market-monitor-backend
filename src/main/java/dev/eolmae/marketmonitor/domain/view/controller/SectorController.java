package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateMarketRanking;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/sector")
@RestController
@RequiredArgsConstructor
public class SectorController {

    private final MarketMapQueryService marketMapQueryService;

    @GetMapping
    public SnapshotResponse<CategoryChangeRateMarketRanking> getCategoryChangeRates(
            @RequestParam MarketQuery market, @RequestParam(defaultValue = "15") int beforeMinutes) {
        return marketMapQueryService.getCategoryChangeRates(market, beforeMinutes);
    }
}
