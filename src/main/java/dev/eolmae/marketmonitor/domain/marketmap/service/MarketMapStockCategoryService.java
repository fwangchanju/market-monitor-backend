package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.marketmap.dto.StockCategoryListItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 종목의 카테고리 배정/재배정. */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketMapStockCategoryService {

    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final StockInfoCacheService stockInfoCacheService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;

    public void assign(String stockCode, Long categoryId) {
        if (!marketMapCategoryRepository.existsById(categoryId)) {
            throw new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId);
        }

        marketMapStockCategoryRepository
                .findById(stockCode)
                .ifPresentOrElse(
                        stockCategory -> stockCategory.reassign(categoryId),
                        () -> marketMapStockCategoryRepository.save(
                                MarketMapStockCategory.create(stockCode, categoryId)));
    }

    @Transactional(readOnly = true)
    public List<StockCategoryListItem> getStockCategories() {
        Map<Long, MarketMapCategory> categoryById = marketMapCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MarketMapCategory::getId, Function.identity()));
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        Map<String, SectorPriceSnapshot> latestPriceByStockCode =
                sectorPriceSnapshotService.findLatestPriceByStockCode();

        return marketMapStockCategoryRepository.findAll().stream()
                .map(stockCategory ->
                        toStockCategoryListItem(stockCategory, categoryById, stockInfoCache, latestPriceByStockCode))
                .toList();
    }

    private StockCategoryListItem toStockCategoryListItem(
            MarketMapStockCategory stockCategory,
            Map<Long, MarketMapCategory> categoryById,
            Map<String, StockInfo> stockInfoCache,
            Map<String, SectorPriceSnapshot> latestPriceByStockCode) {
        StockInfo stockInfo = stockInfoCache.get(stockCategory.getStockCode());
        MarketMapCategory category = categoryById.get(stockCategory.getCategoryId());
        // 최상위 카테고리에 직접 배정된 경우 부모가 없으므로, 자기 자신을 대분류로 노출
        MarketMapCategory parent = categoryById.getOrDefault(category.getParentId(), category);

        SectorPriceSnapshot priceSnapshot = latestPriceByStockCode.get(stockCategory.getStockCode());

        return new StockCategoryListItem(
                stockCategory.getStockCode(),
                stockInfo.getStockName(),
                stockInfo.getMarketType(),
                priceSnapshot == null
                        ? null
                        : priceSnapshot.getCurrentPrice().multiply(BigDecimal.valueOf(stockInfo.getListCount())),
                category.getId(),
                parent.getName(),
                category.getParentId() == null ? null : category.getName());
    }
}
