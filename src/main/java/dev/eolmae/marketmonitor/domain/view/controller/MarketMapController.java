package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketMapScaleResponse;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapScaleService;
import dev.eolmae.marketmonitor.domain.stock.service.MarketMapExcludedStockService;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.ExcludedStockItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/market-map")
@RestController
@RequiredArgsConstructor
public class MarketMapController {

    private final MarketMapQueryService marketMapQueryService;
    private final MarketMapExcludedStockService marketMapExcludedStockService;
    private final MarketMapCategoryService marketMapCategoryService;
    private final MarketMapScaleService marketMapScaleService;
    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;

    @GetMapping
    public SnapshotResponse<MarketMapCategoryNode> getMarketMap(
            @RequestParam Market market, @RequestParam boolean isCustom) {
        return isCustom ? marketMapQueryService.getCustomMarketMap(market) : marketMapQueryService.getDefaultMarketMap(market);
    }

    @GetMapping("/category-change-rates")
    public SnapshotResponse<CategoryChangeRateItem> getCategoryChangeRates(
            @RequestParam Market market, @RequestParam(defaultValue = "60") int beforeMinutes) {
        return marketMapCategoryChangeRateSnapshotService.findLatestRanking(market, beforeMinutes);
    }

    @GetMapping("/scale")
    public MarketMapScaleResponse getScale() {
        return marketMapScaleService.getScale();
    }

    @GetMapping("/excluded-stocks")
    public List<ExcludedStockItem> getExcludedStocks() {
        return marketMapQueryService.listExcludedStocks();
    }

    @PostMapping("/excluded-stocks/{stockCode}")
    public void registerExcludedStock(@PathVariable String stockCode) {
        marketMapExcludedStockService.register(stockCode);
    }

    @DeleteMapping("/excluded-stocks/{stockCode}")
    public void unregisterExcludedStock(@PathVariable String stockCode) {
        marketMapExcludedStockService.unregister(stockCode);
    }

    @DeleteMapping("/excluded-stocks")
    public void deleteExcludedStocks() {
        marketMapExcludedStockService.deleteAll();
    }

    @PostMapping("/excluded-categories/{categoryId}")
    public void registerExcludedCategory(@PathVariable Long categoryId) {
        marketMapCategoryService.exclude(categoryId);
    }

    @DeleteMapping("/excluded-categories/{categoryId}")
    public void unregisterExcludedCategory(@PathVariable Long categoryId) {
        marketMapCategoryService.include(categoryId);
    }

    @DeleteMapping("/excluded-categories")
    public void deleteExcludedCategories() {
        marketMapCategoryService.resetExcludes();
    }

    @DeleteMapping("/reset")
    public void resetMarketMapCustomizations() {
        marketMapExcludedStockService.deleteAll();
        marketMapCategoryService.resetExcludes();
    }
}
