package dev.eolmae.marketmonitor.domain.custom.service;

import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.custom.dto.BulkAssignResponse;
import dev.eolmae.marketmonitor.domain.custom.dto.StockSectorListItem;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class CustomStockSectorService {

    private final CustomStockSectorRepository customStockSectorRepository;
    private final CustomSectorRepository customSectorRepository;
    private final StockInfoCacheService stockInfoCacheService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final CustomValueTierThresholdService customValueTierThresholdService;

    public void assign(String stockCode, Long categoryId) {
        if (!customSectorRepository.existsById(categoryId)) {
            throw new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId);
        }

        customStockSectorRepository
                .findById(stockCode)
                .ifPresentOrElse(
                        stockCategory -> stockCategory.reassign(categoryId),
                        () -> customStockSectorRepository.save(CustomStockSector.create(stockCode, categoryId)));
    }

    /** 여러 종목을 한 카테고리로 한 번에 재배정한다. 건별 assign을 반복 호출하는 대신 조회를 한 번만 수행.
     * 화면 목록 자체가 custom_stock_sector 기준이라 요청으로 들어온 stockCode는 이미 존재하는 것이 정상이며,
     * 그 사이 삭제되는 등의 이유로 조회되지 않은 stockCode만 실패 목록으로 돌려준다. */
    public BulkAssignResponse bulkAssign(List<String> stockCodes, Long categoryId) {
        if (!customSectorRepository.existsById(categoryId)) {
            throw new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId);
        }

        Set<String> remaining = new HashSet<>(stockCodes);
        for (CustomStockSector stockCategory : customStockSectorRepository.findAllById(stockCodes)) {
            stockCategory.reassign(categoryId);
            remaining.remove(stockCategory.getStockCode());
        }

        return new BulkAssignResponse(new ArrayList<>(remaining), categoryId);
    }

    public void updateAlias(String stockCode, String alias) {
        CustomStockSector stockCategory = customStockSectorRepository
                .findById(stockCode)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STOCK_CATEGORY_NOT_FOUND, stockCode));
        stockCategory.updateAlias(alias);
    }

    @Transactional(readOnly = true)
    public SnapshotResponse<StockSectorListItem> getStockCategories() {
        Map<Long, CustomSector> categoryById = customSectorRepository.findAll().stream()
                .collect(Collectors.toMap(CustomSector::getId, Function.identity()));
        Map<String, CustomStockSector> stockCategoryByStockCode = customStockSectorRepository.findAll().stream()
                .collect(Collectors.toMap(CustomStockSector::getStockCode, Function.identity()));
        Map<String, SectorPriceSnapshot> latestPriceByStockCode =
                sectorPriceSnapshotService.findLatestPriceByStockCode();
        List<CustomValueTierThreshold> sortedTiers = customValueTierThresholdService.findAllSortedAscending();

        List<StockSectorListItem> items = stockInfoCacheService.getCache().values().stream()
                .filter(StockInfo::isActiveAndOrdinary)
                .map(stockInfo -> toStockCategoryListItem(
                        stockInfo,
                        stockCategoryByStockCode.get(stockInfo.getStockCode()),
                        categoryById,
                        latestPriceByStockCode,
                        sortedTiers))
                .toList();

        LocalDateTime snapshotTime = latestPriceByStockCode.values().stream()
                .findFirst()
                .map(SectorPriceSnapshot::getSnapshotTime)
                .orElse(null);
        return new SnapshotResponse<>(snapshotTime, items);
    }

    private StockSectorListItem toStockCategoryListItem(
            StockInfo stockInfo,
            CustomStockSector stockCategory,
            Map<Long, CustomSector> categoryById,
            Map<String, SectorPriceSnapshot> latestPriceByStockCode,
            List<CustomValueTierThreshold> sortedTiers) {
        CustomSector category = categoryById.get(stockCategory.getSectorId());
        CustomSector parent = category.hasNoParent() ? null : categoryById.get(category.getParentId());

        SectorPriceSnapshot priceSnapshot = latestPriceByStockCode.get(stockInfo.getStockCode());
        BigDecimal totalMarketValue = null;
        String marketValueTier = null;
        if (priceSnapshot != null) {
            totalMarketValue = priceSnapshot.getCurrentPrice().multiply(BigDecimal.valueOf(stockInfo.getListCount()));
            marketValueTier = customValueTierThresholdService.resolveTier(sortedTiers, totalMarketValue);
        }

        return new StockSectorListItem(
                stockInfo.getStockCode(),
                stockInfo.getMarketType(),
                stockInfo.getStockName(),
                stockCategory.getAlias(),
                totalMarketValue,
                marketValueTier,
                stockInfo.getIndustryName(),
                parent == null ? null : parent.getName(),
                category.getName(),
                category.getId());
    }
}
