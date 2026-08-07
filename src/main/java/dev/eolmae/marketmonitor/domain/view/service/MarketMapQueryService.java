package dev.eolmae.marketmonitor.domain.view.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketMapExcludedStock;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketMapExcludedStockRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.view.dto.ExcludedStockItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketMapQueryService {

    private static final String UNCATEGORIZED = "미분류";
    private static final long ROOT_KEY = 0L;

    private final StockInfoCacheService stockInfoCacheService;
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;

    /** 기본 마켓맵: stock_info 카테고리 그대로(override 없이) 기준, 자식 없는 1뎁스 노드로 감싸서 반환 (getCustomMarketMap과 응답 모양 통일) */
    public SnapshotResponse<MarketMapCategoryNode> getDefaultMarketMap(Market market, boolean isExclude) {
        LocalDateTime latestSnapshotTime = sectorPriceSnapshotRepository
                .findFirstByMarketTypeOrderBySnapshotTimeDesc(market)
                .map(SectorPriceSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        List<StockInfo> candidates = filterCandidates(market, isExclude);
        Map<String, SectorPriceSnapshot> priceMap =
                sectorPriceSnapshotRepository.findByMarketTypeAndSnapshotTime(market, latestSnapshotTime).stream()
                        .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));

        Map<String, List<MarketMapItem>> grouped = candidates.stream()
                .collect(Collectors.groupingBy(
                        stockInfo -> normalizeCategoryName(stockInfo.getCategoryName()),
                        Collectors.mapping(stockInfo -> toMarketMapItem(stockInfo, priceMap), Collectors.toList())));

        List<MarketMapCategoryNode> nodes = grouped.entrySet().stream()
                .map(entry -> {
                    List<MarketMapItem> items = entry.getValue();
                    BigDecimal totalMarketValue = items.stream()
                            .map(MarketMapItem::totalMarketValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new MarketMapCategoryNode(entry.getKey(), totalMarketValue, List.of(), items);
                })
                .toList();

        return new SnapshotResponse<>(latestSnapshotTime, nodes);
    }

    /** 커스텀 마켓맵: 어드민이 구성한 카테고리 트리 기준. 트리에 배정 안 된 종목은 stock_info 카테고리로 묶은 노드를 같은 레벨에 섞어서 반환 */
    public SnapshotResponse<MarketMapCategoryNode> getCustomMarketMap(Market market, boolean isExclude) {
        LocalDateTime latestSnapshotTime = sectorPriceSnapshotRepository
                .findFirstByMarketTypeOrderBySnapshotTimeDesc(market)
                .map(SectorPriceSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        List<StockInfo> candidates = filterCandidates(market, isExclude);
        List<MarketMapCategory> categories = marketMapCategoryRepository.findAll();
        Map<String, MarketMapCategory> categoryByName = new HashMap<>();
        Map<Long, List<MarketMapCategory>> childrenByParentId = new HashMap<>();
        for (MarketMapCategory category : categories) {
            categoryByName.put(category.getName(), category);
            Long parentKey = category.getParentId() == null ? ROOT_KEY : category.getParentId();
            childrenByParentId
                    .computeIfAbsent(parentKey, key -> new ArrayList<>())
                    .add(category);
        }
        Map<String, MarketMapStockCategory> stockCategoryMap = marketMapStockCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MarketMapStockCategory::getStockCode, Function.identity()));
        Map<String, SectorPriceSnapshot> priceMap =
                sectorPriceSnapshotRepository.findByMarketTypeAndSnapshotTime(market, latestSnapshotTime).stream()
                        .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));

        Map<Long, List<MarketMapItem>> itemsByCategoryId = candidates.stream()
                .collect(Collectors.groupingBy(
                        stockInfo -> resolveCategoryId(stockInfo, stockCategoryMap, categoryByName),
                        Collectors.mapping(stockInfo -> toMarketMapItem(stockInfo, priceMap), Collectors.toList())));

        List<MarketMapCategoryNode> nodes = childrenByParentId.getOrDefault(ROOT_KEY, List.of()).stream()
                .sorted(Comparator.comparingInt(MarketMapCategory::getDisplayOrder))
                .map(category -> toCategoryNode(category, childrenByParentId, itemsByCategoryId))
                .toList();

        return new SnapshotResponse<>(latestSnapshotTime, nodes);
    }

    private MarketMapCategoryNode toCategoryNode(
            MarketMapCategory category,
            Map<Long, List<MarketMapCategory>> childrenByParentId,
            Map<Long, List<MarketMapItem>> itemsByCategoryId) {
        List<MarketMapCategoryNode> children = childrenByParentId.getOrDefault(category.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(MarketMapCategory::getDisplayOrder))
                .map(child -> toCategoryNode(child, childrenByParentId, itemsByCategoryId))
                .toList();
        List<MarketMapItem> items = itemsByCategoryId.getOrDefault(category.getId(), List.of());
        BigDecimal itemsValue =
                items.stream().map(MarketMapItem::totalMarketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal childrenValue =
                children.stream().map(MarketMapCategoryNode::totalMarketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MarketMapCategoryNode(category.getName(), itemsValue.add(childrenValue), children, items);
    }

    /** 명시적 배정(market_map_stock_category)이 있으면 그 카테고리, 없으면 stock_info 카테고리명(미분류 포함)으로 실제 카테고리에서 이름 매치. 수집기가 모든 카테고리명을 미리 생성해두므로 항상 매치된다고 전제한다. */
    private Long resolveCategoryId(
            StockInfo stockInfo,
            Map<String, MarketMapStockCategory> stockCategoryMap,
            Map<String, MarketMapCategory> categoryByName) {
        MarketMapStockCategory assignment = stockCategoryMap.get(stockInfo.getStockCode());
        if (assignment != null) {
            return assignment.getCategoryId();
        }
        return categoryByName
                .get(normalizeCategoryName(stockInfo.getCategoryName()))
                .getId();
    }

    private List<StockInfo> filterCandidates(Market market, boolean isExclude) {
        List<StockInfo> candidates = stockInfoCacheService.getCache().values().stream()
                .filter(stockInfo -> stockInfo.getMarketType() == market)
                .filter(StockInfo::isActive)
                .filter(stockInfo -> StockMarketCode.isOrdinaryShare(stockInfo.getMarketCode()))
                .toList();

        if (!isExclude) {
            return candidates;
        }

        Set<String> excludedCodes = marketMapExcludedStockRepository.findByIsActiveTrue().stream()
                .map(MarketMapExcludedStock::getStockCode)
                .collect(Collectors.toSet());

        return candidates.stream()
                .filter(stockInfo -> !excludedCodes.contains(stockInfo.getStockCode()))
                .toList();
    }

    private String normalizeCategoryName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return UNCATEGORIZED;
        }
        return categoryName;
    }

    private MarketMapItem toMarketMapItem(StockInfo stockInfo, Map<String, SectorPriceSnapshot> priceMap) {
        BigDecimal currentPrice = BigDecimal.ZERO;
        BigDecimal changeRate = BigDecimal.ZERO;
        SectorPriceSnapshot priceSnapshot = priceMap.get(stockInfo.getStockCode());
        if (priceSnapshot != null) {
            currentPrice = priceSnapshot.getCurrentPrice();
            changeRate = priceSnapshot.getChangeRate();
        }
        BigDecimal totalMarketValue = currentPrice.multiply(BigDecimal.valueOf(stockInfo.getListCount()));

        return new MarketMapItem(
                stockInfo.getStockCode(),
                stockInfo.getStockName(),
                stockInfo.getLastPrice(),
                totalMarketValue,
                changeRate);
    }

    /** 마켓맵 표시 제외 종목 목록 */
    public List<ExcludedStockItem> listExcludedStocks() {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return marketMapExcludedStockRepository.findByIsActiveTrue().stream()
                .map(excluded -> new ExcludedStockItem(
                        excluded.getStockCode(), resolveStockName(excluded.getStockCode(), stockInfoCache)))
                .toList();
    }

    private String resolveStockName(String stockCode, Map<String, StockInfo> stockInfoCache) {
        StockInfo stockInfo = stockInfoCache.get(stockCode);
        return stockInfo != null ? stockInfo.getStockName() : stockCode;
    }
}
