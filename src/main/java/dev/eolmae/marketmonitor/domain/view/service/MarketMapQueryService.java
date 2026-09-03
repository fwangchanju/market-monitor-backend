package dev.eolmae.marketmonitor.domain.view.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketMapExcludedStockRepository;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.ExcludedStockItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final long NO_PARENT_KEY = 0L;
    /** 기본 마켓맵은 어드민이 구성한 카테고리 트리를 안 쓰므로 exclude 판정 대상 자체가 아님 — id는 관례상 0 고정 */
    private static final Long NO_CATEGORY_ID = 0L;

    private final StockInfoCacheService stockInfoCacheService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;
    private final MarketValueTierThresholdService marketValueTierThresholdService;

    /** 기본 마켓맵: stock_info 카테고리 그대로(override 없이) 기준, 자식 없는 1뎁스 노드로 감싸서 반환 (getCustomMarketMap과 응답 모양 통일) */
    public SnapshotResponse<MarketMapCategoryNode> getDefaultMarketMap(MarketQuery marketQuery) {
        List<Market> markets = marketQuery.toMarkets();
        return sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(markets)
                .map(latestSnapshotTime -> buildDefaultMarketMap(markets, latestSnapshotTime))
                .orElseGet(SnapshotResponse::empty);
    }

    private SnapshotResponse<MarketMapCategoryNode> buildDefaultMarketMap(
            List<Market> markets, LocalDateTime latestSnapshotTime) {
        List<StockInfo> candidates = filterCandidates(markets);
        Map<String, SectorPriceSnapshot> priceMap =
                sectorPriceSnapshotService.findPriceByStockCode(markets, latestSnapshotTime);
        List<MarketValueTierThreshold> sortedTiers = marketValueTierThresholdService.findAllSortedAscending();

        Map<String, List<MarketMapItem>> grouped = candidates.stream()
                .filter(stockInfo -> priceMap.containsKey(stockInfo.getStockCode()))
                .collect(Collectors.groupingBy(
                        stockInfo -> normalizeCategoryName(stockInfo.getCategoryName()),
                        Collectors.mapping(
                                stockInfo -> toMarketMapItem(stockInfo, priceMap.get(stockInfo.getStockCode()), sortedTiers),
                                Collectors.toList())));

        List<MarketMapCategoryNode> nodes = grouped.entrySet().stream()
                .map(entry -> {
                    List<MarketMapItem> items = entry.getValue();
                    BigDecimal totalMarketValue = items.stream()
                            .map(MarketMapItem::totalMarketValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return MarketMapCategoryNode.leaf(NO_CATEGORY_ID, entry.getKey(), totalMarketValue, items);
                })
                .toList();

        return new SnapshotResponse<>(latestSnapshotTime, nodes);
    }

    /** 커스텀 마켓맵: 어드민이 구성한 카테고리 트리 기준. 트리에 배정 안 된 종목은 stock_info 카테고리로 묶은 노드를 같은 레벨에 섞어서 반환 */
    public SnapshotResponse<MarketMapCategoryNode> getCustomMarketMap(MarketQuery marketQuery) {
        List<Market> markets = marketQuery.toMarkets();
        return sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(markets)
                .map(latestSnapshotTime -> buildCustomMarketMap(markets, latestSnapshotTime))
                .orElseGet(SnapshotResponse::empty);
    }

    private SnapshotResponse<MarketMapCategoryNode> buildCustomMarketMap(
            List<Market> markets, LocalDateTime latestSnapshotTime) {
        // 등락률 데코레이션(tierBreakdown)은 카테고리별로 하나만 붙으므로, All Stocks처럼 markets가
        // 여러 개여도 마켓별로 나눌 필요 없이 그대로 합쳐서 조회한다.
        Map<Long, List<CategoryTierBreakdown>> tierBreakdownByCategoryId =
                marketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId(markets, latestSnapshotTime).values().stream()
                        .flatMap(byCategoryId -> byCategoryId.entrySet().stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                            List<CategoryTierBreakdown> merged = new ArrayList<>(a);
                            merged.addAll(b);
                            return merged;
                        }));
        List<MarketMapCategoryNode> tree = buildCategoryTree(markets, latestSnapshotTime, tierBreakdownByCategoryId);
        return new SnapshotResponse<>(latestSnapshotTime, tree);
    }

    /**
     * 카테고리 등락률 스냅샷 캡처(CollectionScheduler)용 원본 트리. 변화율 데코레이션은 필요 없어서(어차피
     * 안 쓰임) buildCategoryTree만 노출한다. snapshotTime은 호출부(지수기여도 수집 직후)가 이미 들고 있는
     * 값을 그대로 받는다 — "최신 시각"을 다시 조회하면, 이번 사이클에 특정 market 수집이 실패했을 때 예전
     * 시각 데이터를 지금 시각 라벨로 잘못 저장하게 된다. 대신 정확히 이 snapshotTime에 데이터가 없으면 빈
     * 트리를 반환해서 호출부가 스킵하도록 한다.
     */
    public List<MarketMapCategoryNode> getCustomMarketMapTree(Market market, LocalDateTime snapshotTime) {
        if (sectorPriceSnapshotService.notExistsSnapshot(market, snapshotTime)) {
            return List.of();
        }
        return buildCategoryTree(List.of(market), snapshotTime);
    }

    private List<MarketMapCategoryNode> buildCategoryTree(List<Market> markets, LocalDateTime latestSnapshotTime) {
        return buildCategoryTree(markets, latestSnapshotTime, Map.of());
    }

    private List<MarketMapCategoryNode> buildCategoryTree(
            List<Market> markets,
            LocalDateTime latestSnapshotTime,
            Map<Long, List<CategoryTierBreakdown>> tierBreakdownByCategoryId) {
        List<StockInfo> candidates = filterCandidates(markets);
        List<MarketMapCategory> categories = marketMapCategoryRepository.findAll();
        Map<Long, List<MarketMapCategory>> childrenByParentId = new HashMap<>();
        for (MarketMapCategory category : categories) {
            Long parentKey = category.hasNoParent() ? NO_PARENT_KEY : category.getParentId();
            childrenByParentId
                    .computeIfAbsent(parentKey, key -> new ArrayList<>())
                    .add(category);
        }
        Map<String, MarketMapStockCategory> stockCategoryMap = findStockCategoryMap();
        Map<String, SectorPriceSnapshot> priceMap =
                sectorPriceSnapshotService.findPriceByStockCode(markets, latestSnapshotTime);
        List<MarketValueTierThreshold> sortedTiers = marketValueTierThresholdService.findAllSortedAscending();

        Map<Long, List<MarketMapItem>> itemsByCategoryId = candidates.stream()
                .filter(stockInfo -> priceMap.containsKey(stockInfo.getStockCode()))
                .collect(Collectors.groupingBy(
                        stockInfo -> stockCategoryMap.get(stockInfo.getStockCode()).getCategoryId(),
                        Collectors.mapping(
                                stockInfo -> toMarketMapItem(
                                        stockInfo, priceMap.get(stockInfo.getStockCode()), stockCategoryMap, sortedTiers),
                                Collectors.toList())));

        return childrenByParentId.getOrDefault(NO_PARENT_KEY, List.of()).stream()
                .map(category -> toCategoryNode(category, childrenByParentId, itemsByCategoryId, tierBreakdownByCategoryId))
                .toList();
    }

    private MarketMapCategoryNode toCategoryNode(
            MarketMapCategory category,
            Map<Long, List<MarketMapCategory>> childrenByParentId,
            Map<Long, List<MarketMapItem>> itemsByCategoryId,
            Map<Long, List<CategoryTierBreakdown>> tierBreakdownByCategoryId) {
        List<MarketMapCategoryNode> children = childrenByParentId.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> toCategoryNode(child, childrenByParentId, itemsByCategoryId, tierBreakdownByCategoryId))
                .toList();
        List<MarketMapItem> items = itemsByCategoryId.getOrDefault(category.getId(), List.of());
        BigDecimal itemsValue =
                items.stream().map(MarketMapItem::totalMarketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal childrenValue =
                children.stream().map(MarketMapCategoryNode::totalMarketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryTierBreakdown> tierBreakdown = tierBreakdownByCategoryId.getOrDefault(category.getId(), List.of());
        return new MarketMapCategoryNode(
                category.getId(),
                category.getName(),
                category.isExcluded(),
                itemsValue.add(childrenValue),
                tierBreakdown,
                children,
                items);
    }

    private List<StockInfo> filterCandidates(List<Market> markets) {
        return stockInfoCacheService.getCache().values().stream()
                .filter(stockInfo -> markets.contains(stockInfo.getMarketType()))
                .filter(StockInfo::isActiveAndOrdinary)
                .toList();
    }

    private String normalizeCategoryName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return UNCATEGORIZED;
        }
        return categoryName;
    }

    private Map<String, MarketMapStockCategory> findStockCategoryMap() {
        return marketMapStockCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MarketMapStockCategory::getStockCode, Function.identity()));
    }

    /** 기본 마켓맵용: override 없이 stock_info 종목명 그대로 */
    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo, SectorPriceSnapshot priceSnapshot, List<MarketValueTierThreshold> sortedTiers) {
        return toMarketMapItem(stockInfo, priceSnapshot, stockInfo.getStockName(), sortedTiers);
    }

    /** 커스텀 마켓맵용: market_map_stock_category에 alias가 있으면 그걸로 종목명 대체 */
    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo,
            SectorPriceSnapshot priceSnapshot,
            Map<String, MarketMapStockCategory> stockCategoryMap,
            List<MarketValueTierThreshold> sortedTiers) {
        return toMarketMapItem(stockInfo, priceSnapshot, resolveDisplayName(stockInfo, stockCategoryMap), sortedTiers);
    }

    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo,
            SectorPriceSnapshot priceSnapshot,
            String displayName,
            List<MarketValueTierThreshold> sortedTiers) {
        BigDecimal currentPrice = priceSnapshot.getCurrentPrice();
        BigDecimal changeRate = priceSnapshot.getChangeRate();
        BigDecimal totalMarketValue = currentPrice.multiply(BigDecimal.valueOf(stockInfo.getListCount()));

        return new MarketMapItem(
                stockInfo.getStockCode(),
                displayName,
                currentPrice,
                stockInfo.getLastPrice(),
                totalMarketValue,
                marketValueTierThresholdService.resolveTier(sortedTiers, totalMarketValue),
                changeRate,
                priceSnapshot.getSnapshotTime());
    }

    /** alias가 배정되어 있으면 alias, 없으면 stock_info의 종목명을 그대로 노출 */
    private String resolveDisplayName(StockInfo stockInfo, Map<String, MarketMapStockCategory> stockCategoryMap) {
        MarketMapStockCategory stockCategory = stockCategoryMap.get(stockInfo.getStockCode());
        if (stockCategory == null || stockCategory.getAlias() == null || stockCategory.getAlias().isBlank()) {
            return stockInfo.getStockName();
        }
        return stockCategory.getAlias();
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
