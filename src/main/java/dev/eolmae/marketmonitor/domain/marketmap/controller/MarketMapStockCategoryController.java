package dev.eolmae.marketmonitor.domain.marketmap.controller;

import dev.eolmae.marketmonitor.domain.marketmap.dto.AliasRequest;
import dev.eolmae.marketmonitor.domain.marketmap.dto.BulkAssignRequest;
import dev.eolmae.marketmonitor.domain.marketmap.dto.BulkAssignResponse;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryIdRequest;
import dev.eolmae.marketmonitor.domain.marketmap.dto.StockCategoryListItem;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapStockCategoryService;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/admin/market-map/stock-categories")
@RestController
@RequiredArgsConstructor
public class MarketMapStockCategoryController {

    private final MarketMapStockCategoryService marketMapStockCategoryService;

    @GetMapping
    public SnapshotResponse<StockCategoryListItem> getStockCategories() {
        return marketMapStockCategoryService.getStockCategories();
    }

    @PutMapping("/{stockCode}")
    public void assign(@PathVariable String stockCode, @RequestBody @Valid CategoryIdRequest request) {
        marketMapStockCategoryService.assign(stockCode, request.categoryId());
    }

    @PatchMapping("/bulk")
    public BulkAssignResponse bulkAssign(@RequestBody @Valid BulkAssignRequest request) {
        return marketMapStockCategoryService.bulkAssign(request.stockCodes(), request.categoryId());
    }

    @PatchMapping("/{stockCode}/alias")
    public void updateAlias(@PathVariable String stockCode, @RequestBody @Valid AliasRequest request) {
        marketMapStockCategoryService.updateAlias(stockCode, request.alias());
    }
}
