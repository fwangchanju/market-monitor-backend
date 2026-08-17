package dev.eolmae.marketmonitor.domain.marketmap.controller;

import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryIdRequest;
import dev.eolmae.marketmonitor.domain.marketmap.dto.StockCategoryListItem;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapStockCategoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
    public List<StockCategoryListItem> getStockCategories() {
        return marketMapStockCategoryService.getStockCategories();
    }

    @PutMapping("/{stockCode}")
    public void assign(@PathVariable String stockCode, @RequestBody @Valid CategoryIdRequest request) {
        marketMapStockCategoryService.assign(stockCode, request.categoryId());
    }
}
