package dev.eolmae.marketmonitor.domain.marketmap.controller;

import dev.eolmae.marketmonitor.domain.marketmap.dto.AssignStockRequest;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapStockCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/market-map/stock-categories")
@RequiredArgsConstructor
public class MarketMapStockCategoryController {

    private final MarketMapStockCategoryService marketMapStockCategoryService;

    @PutMapping("/{stockCode}")
    public void assign(@PathVariable String stockCode, @RequestBody AssignStockRequest request) {
        marketMapStockCategoryService.assign(stockCode, request.categoryId());
    }
}
