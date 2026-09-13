package dev.eolmae.marketmonitor.domain.view.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketValueTierItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketMapExcludedStockRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateMarketRanking;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryRankingSummary;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.ExcludedStockItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketIndexChangeRate;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketmonitor.domain.view.dto.MarketOverviewItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.dto.TopCategoryItem;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
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
    private static final long NO_PARENT_KEY = 0L;
    /** 기본 마켓맵은 어드민이 구성한 카테고리 트리를 안 쓰므로 exclude 판정 대상 자체가 아님 — id는 관례상 0 고정 */
    private static final Long NO_CATEGORY_ID = 0L;

    private static final int TOP_N = 3;

    private final StockInfoCacheService stockInfoCacheService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;
    private final MarketValueTierThresholdService marketValueTierThresholdService;
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;

    /** 기본 마켓맵: stock_info 카테고리 그대로(override 없이) 기준, 자식 없는 1뎁스 노드로 감싸서 반환 (getCustomMarketMap과 응답 모양 통일) */
    public MarketMapResponse getDefaultMarketMap(MarketQuery marketQuery) {
        List<Market> markets = marketQuery.toMarkets();
        return sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(markets)
                .map(latestSnapshotTime -> buildDefaultMarketMap(markets, latestSnapshotTime))
                .orElseGet(MarketMapResponse::empty);
    }

    private MarketMapResponse buildDefaultMarketMap(List<Market> markets, LocalDateTime latestSnapshotTime) {
        List<StockInfo> candidates = filterCandidates(markets);
        Map<String, SectorPriceSnapshot> priceMap =
                sectorPriceSnapshotService.findPriceByStockCode(markets, latestSnapshotTime);
        List<MarketValueTierThreshold> sortedTiers = marketValueTierThresholdService.findAllSortedAscending();

        Map<String, List<MarketMapItem>> grouped = candidates.stream()
                .filter(stockInfo -> priceMap.containsKey(stockInfo.getStockCode()))
                .collect(Collectors.groupingBy(
                        stockInfo -> normalizeCategoryName(stockInfo.getCategoryName()),
                        Collectors.mapping(
                                stockInfo ->
                                        toMarketMapItem(stockInfo, priceMap.get(stockInfo.getStockCode()), sortedTiers),
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

        return new MarketMapResponse(latestSnapshotTime, nodes, findSingleMarketOverview(markets, latestSnapshotTime));
    }

    /** 커스텀 마켓맵: 어드민이 구성한 카테고리 트리 기준. 트리에 배정 안 된 종목은 stock_info 카테고리로 묶은 노드를 같은 레벨에 섞어서 반환 */
    public MarketMapResponse getCustomMarketMap(MarketQuery marketQuery) {
        List<Market> markets = marketQuery.toMarkets();
        return sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(markets)
                .map(latestSnapshotTime -> buildCustomMarketMap(markets, latestSnapshotTime))
                .orElseGet(MarketMapResponse::empty);
    }

    private MarketMapResponse buildCustomMarketMap(List<Market> markets, LocalDateTime latestSnapshotTime) {
        // 등락률 데코레이션(tierBreakdown)은 카테고리별로 하나만 붙으므로, All Stocks처럼 markets가
        // 여러 개여도 마켓별로 나눌 필요 없이 그대로 합쳐서 조회한다.
        Map<Long, List<CategoryTierBreakdown>> tierBreakdownByCategoryId =
                marketMapCategoryChangeRateSnapshotService
                        .findTierBreakdownsByCategoryId(markets, latestSnapshotTime)
                        .values()
                        .stream()
                        .flatMap(byCategoryId -> byCategoryId.entrySet().stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                            List<CategoryTierBreakdown> merged = new ArrayList<>(a);
                            merged.addAll(b);
                            return merged;
                        }));
        List<MarketMapCategoryNode> tree = buildCategoryTree(markets, latestSnapshotTime, tierBreakdownByCategoryId);
        return new MarketMapResponse(latestSnapshotTime, tree, findSingleMarketOverview(markets, latestSnapshotTime));
    }

    /**
     * 마켓맵 카테고리별 등락률 랭킹(섹터 페이지) — markets 전부가 공통으로 가진 최신 시각을 구한 뒤
     * 시각 인자 변형에 위임한다.
     */
    public SnapshotResponse<CategoryChangeRateMarketRanking> getCategoryChangeRates(
            MarketQuery marketQuery, int beforeMinutes) {
        return marketMapCategoryChangeRateSnapshotService
                .findLatestCommonSnapshotTime(marketQuery.toMarkets())
                .map(snapshotTime -> getCategoryChangeRates(marketQuery, snapshotTime, beforeMinutes))
                .orElseGet(SnapshotResponse::empty);
    }

    /**
     * 스냅샷 시각을 인자로 받는 변형 — 텔레그램 발송 경로처럼 이미 확정된 dataTime을 그대로 써야 하는
     * 호출부(getTopCategoryRankings)용. 그 시각에 지수 스냅샷이 없으면(부분 실패로 아예 없는 경우)
     * 조용히 비워서 내려준다 — 다른 시점 값으로 대체하지 않는다.
     */
    public SnapshotResponse<CategoryChangeRateMarketRanking> getCategoryChangeRates(
            MarketQuery marketQuery, LocalDateTime snapshotTime, int beforeMinutes) {
        List<Market> markets = marketQuery.toMarkets();
        SnapshotResponse<CategoryChangeRateMarketRanking> ranking =
                marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(markets, snapshotTime, beforeMinutes);

        Map<Market, BigDecimal> nowIndexChangeRateByMarket =
                toChangeRateByMarket(findOverviewsBySnapshotTime(snapshotTime));
        Map<Market, BigDecimal> beforeIndexChangeRateByMarket =
                toChangeRateByMarket(findOverviewsBySnapshotTime(snapshotTime.minusMinutes(beforeMinutes)));
        Map<Long, MarketMapCategory> categoryById = marketMapCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MarketMapCategory::getId, Function.identity()));

        return new SnapshotResponse<>(
                snapshotTime,
                ranking.items().stream()
                        .map(marketRanking -> decorateRanking(
                                marketRanking, nowIndexChangeRateByMarket, beforeIndexChangeRateByMarket, categoryById))
                        .toList());
    }

    private Map<Market, BigDecimal> toChangeRateByMarket(Map<Market, MarketOverviewSnapshot> overviewsByMarket) {
        return overviewsByMarket.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().getChangeRate()));
    }

    private CategoryChangeRateMarketRanking decorateRanking(
            CategoryChangeRateMarketRanking marketRanking,
            Map<Market, BigDecimal> nowIndexChangeRateByMarket,
            Map<Market, BigDecimal> beforeIndexChangeRateByMarket,
            Map<Long, MarketMapCategory> categoryById) {
        // 카테고리 버전 복원(MarketMapCategoryTreeService.restore) 직후에는 스냅샷 row가 이미 없어진
        // categoryId를 가리킬 수 있다 — 다음 수집 tick까지 그 항목만 결과에서 뺀다. 잘못된 depth를
        // 채워 넣지 않는다(대분류 판정에 영향을 준다).
        List<CategoryChangeRateItem> items = marketRanking.items().stream()
                .filter(item -> categoryById.containsKey(item.categoryId()))
                .map(item -> decorateWithCategory(item, categoryById))
                .toList();
        MarketIndexChangeRate index = toMarketIndexChangeRate(
                marketRanking.market(), nowIndexChangeRateByMarket, beforeIndexChangeRateByMarket);
        return new CategoryChangeRateMarketRanking(marketRanking.market(), items, index);
    }

    // now가 없으면(그 시각 지수 스냅샷 자체가 없음) index 전체가 null. now가 있으면 before만 null일 수
    // 있다(before 시각에 정확히 일치하는 스냅샷이 없는 경우) — 가까운 다른 시점 값으로 대체하지 않는다.
    private MarketIndexChangeRate toMarketIndexChangeRate(
            Market market,
            Map<Market, BigDecimal> nowIndexChangeRateByMarket,
            Map<Market, BigDecimal> beforeIndexChangeRateByMarket) {
        BigDecimal now = nowIndexChangeRateByMarket.get(market);
        if (now == null) {
            return null;
        }
        return new MarketIndexChangeRate(now, beforeIndexChangeRateByMarket.get(market));
    }

    private CategoryChangeRateItem decorateWithCategory(
            CategoryChangeRateItem item, Map<Long, MarketMapCategory> categoryById) {
        MarketMapCategory category = categoryById.get(item.categoryId());
        return item.withCategory(category.getName(), category.getDepth());
    }

    /**
     * 텔레그램 캡션용 카테고리 TOP3 랭킹 — 대분류(depth 0)만, 기본 제외 구간(market_value_tier_threshold
     * .is_excluded_by_default)을 뺀 가중평균 기준 내림차순 TOP3. 필터·정렬·TOP3 확정까지 전부 여기서
     * 끝내고, notification 쪽(CategoryRankingTextBuilder)은 텍스트 포매팅만 한다. 데이터 없는 마켓은
     * getCategoryChangeRates가 이미 결과에서 뺀 상태라 자동으로 여기서도 빠진다.
     */
    public List<CategoryRankingSummary> getTopCategoryRankings(
            MarketQuery marketQuery, LocalDateTime snapshotTime, int beforeMinutes) {
        SnapshotResponse<CategoryChangeRateMarketRanking> ranking =
                getCategoryChangeRates(marketQuery, snapshotTime, beforeMinutes);

        Set<Long> excludedTierIds = marketValueTierThresholdService.getValueTiers().stream()
                .filter(MarketValueTierItem::isExcludedByDefault)
                .map(MarketValueTierItem::id)
                .collect(Collectors.toSet());

        return ranking.items().stream()
                .map(marketRanking -> toCategoryRankingSummary(marketRanking, excludedTierIds))
                .toList();
    }

    private CategoryRankingSummary toCategoryRankingSummary(
            CategoryChangeRateMarketRanking marketRanking, Set<Long> excludedTierIds) {
        List<TopCategoryItem> topCategories = marketRanking.items().stream()
                .filter(item -> item.depth() == 0)
                .map(item -> toTopCategoryItem(item, excludedTierIds))
                .sorted(Comparator.comparing(TopCategoryItem::changeRate).reversed())
                .limit(TOP_N)
                .toList();
        // 텍스트 캡션은 before를 쓰지 않는다. 헤더에는 현재 등락률만 붙는다.
        BigDecimal indexChangeRate =
                marketRanking.index() != null ? marketRanking.index().now() : null;
        return new CategoryRankingSummary(marketRanking.market(), indexChangeRate, topCategories);
    }

    private TopCategoryItem toTopCategoryItem(CategoryChangeRateItem item, Set<Long> excludedTierIds) {
        List<CategoryTierBreakdown> included = item.now().stream()
                .filter(breakdown -> !excludedTierIds.contains(breakdown.tierId()))
                .toList();
        SnapshotAverages averages = marketMapCategoryChangeRateSnapshotService.combine(included);
        return new TopCategoryItem(item.categoryName(), averages.weightedAvgChangeRate());
    }

    /** markets가 정확히 하나일 때만 의미 있는 단일 지수 개요 — ALL_STOCK처럼 여럿이면 단일 값이 없어 null. */
    private MarketOverviewItem findSingleMarketOverview(List<Market> markets, LocalDateTime snapshotTime) {
        if (markets.size() != 1) {
            return null;
        }
        MarketOverviewSnapshot overview =
                findOverviewsBySnapshotTime(snapshotTime).get(markets.get(0));
        return overview == null ? null : MarketOverviewItem.from(overview);
    }

    private Map<Market, MarketOverviewSnapshot> findOverviewsBySnapshotTime(LocalDateTime snapshotTime) {
        return marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime).stream()
                .collect(Collectors.toMap(MarketOverviewSnapshot::getMarketType, Function.identity()));
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
                        stockInfo ->
                                stockCategoryMap.get(stockInfo.getStockCode()).getCategoryId(),
                        Collectors.mapping(
                                stockInfo -> toMarketMapItem(
                                        stockInfo,
                                        priceMap.get(stockInfo.getStockCode()),
                                        stockCategoryMap,
                                        sortedTiers),
                                Collectors.toList())));

        return childrenByParentId.getOrDefault(NO_PARENT_KEY, List.of()).stream()
                .map(category ->
                        toCategoryNode(category, childrenByParentId, itemsByCategoryId, tierBreakdownByCategoryId))
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

    /** 기본 마켓맵용: alias 없음(커스텀 트리 전용 개념) */
    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo, SectorPriceSnapshot priceSnapshot, List<MarketValueTierThreshold> sortedTiers) {
        return toMarketMapItem(stockInfo, priceSnapshot, (String) null, sortedTiers);
    }

    /** 커스텀 마켓맵용: market_map_stock_category에 배정된 alias(없으면 null)를 같이 실어 보낸다 */
    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo,
            SectorPriceSnapshot priceSnapshot,
            Map<String, MarketMapStockCategory> stockCategoryMap,
            List<MarketValueTierThreshold> sortedTiers) {
        return toMarketMapItem(stockInfo, priceSnapshot, resolveAlias(stockInfo, stockCategoryMap), sortedTiers);
    }

    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo,
            SectorPriceSnapshot priceSnapshot,
            String alias,
            List<MarketValueTierThreshold> sortedTiers) {
        BigDecimal currentPrice = priceSnapshot.getCurrentPrice();
        BigDecimal changeRate = priceSnapshot.getChangeRate();
        BigDecimal totalMarketValue = currentPrice.multiply(BigDecimal.valueOf(stockInfo.getListCount()));

        return new MarketMapItem(
                stockInfo.getStockCode(),
                stockInfo.getStockName(),
                alias,
                currentPrice,
                stockInfo.getLastPrice(),
                totalMarketValue,
                marketValueTierThresholdService.resolveTier(sortedTiers, totalMarketValue),
                changeRate,
                priceSnapshot.getSnapshotTime());
    }

    /** 배정된 alias가 있으면 그 값, 없거나 빈 문자열이면 null */
    private String resolveAlias(StockInfo stockInfo, Map<String, MarketMapStockCategory> stockCategoryMap) {
        MarketMapStockCategory stockCategory = stockCategoryMap.get(stockInfo.getStockCode());
        if (stockCategory == null
                || stockCategory.getAlias() == null
                || stockCategory.getAlias().isBlank()) {
            return null;
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
