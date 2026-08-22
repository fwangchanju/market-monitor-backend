package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.common.enums.MarketValueTier;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.marketmap.dto.BulkAssignResponse;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 여러 종목을 한 카테고리로 한 번에 재배정한다. 건별 assign을 반복 호출하는 대신 조회를 한 번만 수행.
     * 화면 목록 자체가 market_map_stock_category 기준이라 요청으로 들어온 stockCode는 이미 존재하는 것이 정상이며,
     * 그 사이 삭제되는 등의 이유로 조회되지 않은 stockCode만 실패 목록으로 돌려준다. */
    public BulkAssignResponse bulkAssign(List<String> stockCodes, Long categoryId) {
        if (!marketMapCategoryRepository.existsById(categoryId)) {
            throw new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId);
        }

        Set<String> remaining = new HashSet<>(stockCodes);
        for (MarketMapStockCategory stockCategory : marketMapStockCategoryRepository.findAllById(stockCodes)) {
            stockCategory.reassign(categoryId);
            remaining.remove(stockCategory.getStockCode());
        }

        return new BulkAssignResponse(new ArrayList<>(remaining), categoryId);
    }

    public void updateAlias(String stockCode, String alias) {
        MarketMapStockCategory stockCategory = marketMapStockCategoryRepository
                .findById(stockCode)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STOCK_CATEGORY_NOT_FOUND, stockCode));
        stockCategory.updateAlias(alias);
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
        MarketMapCategory parent = category.getParentId() == null ? null : categoryById.get(category.getParentId());

        SectorPriceSnapshot priceSnapshot = latestPriceByStockCode.get(stockCategory.getStockCode());
        BigDecimal totalMarketValue = null;
        MarketValueTier marketValueTier = null;
        if (priceSnapshot != null) {
            totalMarketValue = priceSnapshot.getCurrentPrice().multiply(BigDecimal.valueOf(stockInfo.getListCount()));
            marketValueTier = MarketValueTier.from(totalMarketValue);
        }

        return new StockCategoryListItem(
                stockCategory.getStockCode(),
                stockInfo.getMarketType(),
                stockInfo.getStockName(),
                stockCategory.getAlias(),
                totalMarketValue,
                marketValueTier,
                stockInfo.getCategoryName(),
                parent == null ? null : parent.getName(),
                category.getName(),
                category.getId());
    }
}
