package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.service.MarketMapExcludedStockService;
import dev.eolmae.marketmonitor.domain.stock.service.StockCategoryService;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryGroup;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.dto.StockCategoryRequest;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-map")
@RequiredArgsConstructor
public class MarketMapController {

    private final MarketMapQueryService marketMapQueryService;
    private final MarketMapExcludedStockService marketMapExcludedStockService;
    private final StockCategoryService stockCategoryService;

    @GetMapping
    public SnapshotResponse<MarketMapCategoryGroup> getMarketMap(
            @RequestParam Market market, @RequestParam boolean isExclude) {
        return marketMapQueryService.getMarketMap(market, isExclude);
    }

    @PostMapping("/excluded-stocks/{stockCode}")
    public void registerExcludedStock(@PathVariable String stockCode) {
        marketMapExcludedStockService.register(stockCode);
    }

    @DeleteMapping("/excluded-stocks/{stockCode}")
    public void unregisterExcludedStock(@PathVariable String stockCode) {
        marketMapExcludedStockService.unregister(stockCode);
    }

    @PatchMapping("/categories/{stockCode}")
    public void reassignCategory(@PathVariable String stockCode, @RequestBody StockCategoryRequest request) {
        stockCategoryService.reassign(stockCode, request.categoryName());
    }
}
