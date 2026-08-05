package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.domain.marketmap.dto.StockCategoryItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 종목의 카테고리 배정/재배정. */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketMapStockCategoryService {

    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final StockInfoCacheService stockInfoCacheService;

    public void assign(String stockCode, Long categoryId) {
        if (!marketMapCategoryRepository.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        marketMapStockCategoryRepository
                .findById(stockCode)
                .ifPresentOrElse(
                        stockCategory -> stockCategory.reassign(categoryId),
                        () -> marketMapStockCategoryRepository.save(MarketMapStockCategory.create(stockCode, categoryId)));
    }

    @Transactional(readOnly = true)
    public List<StockCategoryItem> getStockCategories() {
        Map<Long, MarketMapCategory> categoryById = marketMapCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MarketMapCategory::getId, Function.identity()));
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return marketMapStockCategoryRepository.findAll().stream()
                .map(stockCategory -> new StockCategoryItem(
                        stockCategory.getStockCode(),
                        stockInfoCache.get(stockCategory.getStockCode()).getStockName(),
                        categoryById.get(stockCategory.getCategoryId()).getName()))
                .toList();
    }
}
