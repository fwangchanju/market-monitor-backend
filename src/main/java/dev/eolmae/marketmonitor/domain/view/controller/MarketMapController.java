package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.service.MarketMapExcludedStockService;
import dev.eolmae.marketmonitor.domain.stock.service.StockCategoryService;
import dev.eolmae.marketmonitor.domain.view.dto.ExcludedStockItem;
// import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryGroup;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.dto.StockCategoryItem;
import dev.eolmae.marketmonitor.domain.view.dto.StockCategoryRequest;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.util.List;
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

@RequestMapping("/api/market-map")
@RestController
@RequiredArgsConstructor
public class MarketMapController {

    private final MarketMapQueryService marketMapQueryService;
    private final MarketMapExcludedStockService marketMapExcludedStockService;
    private final StockCategoryService stockCategoryService;

    // TODO 화면 점검 후 이상 없으면 삭제
    // @GetMapping
    // public SnapshotResponse<MarketMapCategoryGroup> getMarketMap(
    //         @RequestParam Market market, @RequestParam boolean isExclude) {
    //     return marketMapQueryService.getMarketMap(market, isExclude);
    // }

    @GetMapping
    public SnapshotResponse<MarketMapCategoryNode> getMarketMap(
            @RequestParam Market market, @RequestParam boolean isExclude, @RequestParam boolean isCustom) {
        return isCustom
                ? marketMapQueryService.getCustomMarketMap(market, isExclude)
                : marketMapQueryService.getDefaultMarketMap(market, isExclude);
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

    @GetMapping("/categories")
    public List<StockCategoryItem> getCategoryOverrides() {
        return marketMapQueryService.listStockCategories();
    }

    @PatchMapping("/categories/{stockCode}")
    public void reassignCategory(@PathVariable String stockCode, @RequestBody StockCategoryRequest request) {
        stockCategoryService.reassign(stockCode, request.categoryName());
    }

    @DeleteMapping("/categories/{stockCode}")
    public void deleteCategory(@PathVariable String stockCode) {
        stockCategoryService.delete(stockCode);
    }

    @DeleteMapping("/categories")
    public void deleteCategories() {
        stockCategoryService.deleteAll();
    }

    @DeleteMapping("/reset")
    public void resetMarketMapCustomizations() {
        marketMapExcludedStockService.deleteAll();
        stockCategoryService.deleteAll();
    }
}
