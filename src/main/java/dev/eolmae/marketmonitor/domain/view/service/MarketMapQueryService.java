package dev.eolmae.marketmonitor.domain.view.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketValueTierItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.CategoryTierAggregationService;
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
import dev.eolmae.marketmonitor.domain.view.enums.AverageMode;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final int TOP_N = 2;

    private final StockInfoCacheService stockInfoCacheService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;
    private final CategoryTierAggregationService categoryTierAggregationService;
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
                buildRankingForMarkets(markets, snapshotTime, beforeMinutes);

        Map<Market, BigDecimal> nowIndexChangeRateByMarket =
                toChangeRateByMarket(findOverviewsBySnapshotTime(snapshotTime));
        Map<Market, BigDecimal> beforeIndexChangeRateByMarket =
                toChangeRateByMarket(findOverviewsBySnapshotTime(snapshotTime.minusMinutes(beforeMinutes)));
        Map<Long, MarketMapCategory> categoryById = findCategoryById();

        return new SnapshotResponse<>(
                snapshotTime,
                ranking.items().stream()
                        .map(marketRanking -> decorateRanking(
                                marketRanking, nowIndexChangeRateByMarket, beforeIndexChangeRateByMarket, categoryById))
                        .toList());
    }

    /**
     * markets가 정확히 snapshotTime 시각에 가진 카테고리별 현재/직전(beforeMinutes분 전) 등락률 랭킹 —
     * 마켓마다 그 시각의 커스텀 트리를 빌드해 합산한다(결정 1). 합산 결과(카테고리별 구간 원시 합계)가
     * 빈 마켓은 결과 목록에서 아예 빠진다 — 트리 자체가 비어 있지 않아도(카테고리 노드는 있는데 가격
     * 행이 없어 종목이 0개) 합산 결과는 빌 수 있으므로, 트리가 아니라 합산 결과의 비어 있음으로
     * 판단한다. beforeMinutes 시각에 정확히 일치하는 카테고리가 없으면(장 시작 직후, 수집 gap 등) 해당
     * 카테고리는 before 없이 내려준다 — 가장 가까운 다른 시점 데이터로 조용히 대체하지 않는다.
     */
    private SnapshotResponse<CategoryChangeRateMarketRanking> buildRankingForMarkets(
            List<Market> markets, LocalDateTime snapshotTime, int beforeMinutes) {
        LocalDateTime beforeTime = snapshotTime.minusMinutes(beforeMinutes);
        List<CategoryChangeRateMarketRanking> rankings = markets.stream()
                .map(market -> toMarketRanking(market, snapshotTime, beforeTime))
                .filter(Objects::nonNull)
                .toList();
        return new SnapshotResponse<>(snapshotTime, rankings);
    }

    private CategoryChangeRateMarketRanking toMarketRanking(
            Market market, LocalDateTime snapshotTime, LocalDateTime beforeTime) {
        Map<Long, List<CategoryTierBreakdown>> now =
                categoryTierAggregationService.aggregateByCategory(getCustomMarketMapTree(market, snapshotTime));
        if (now.isEmpty()) {
            return null;
        }
        Map<Long, List<CategoryTierBreakdown>> before =
                categoryTierAggregationService.aggregateByCategory(getCustomMarketMapTree(market, beforeTime));
        List<CategoryChangeRateItem> items = now.entrySet().stream()
                .map(entry -> toItem(entry.getKey(), entry.getValue(), before.get(entry.getKey())))
                .toList();
        return new CategoryChangeRateMarketRanking(market, items);
    }

    private CategoryChangeRateItem toItem(
            Long categoryId, List<CategoryTierBreakdown> now, List<CategoryTierBreakdown> before) {
        if (before == null) {
            return CategoryChangeRateItem.withoutBefore(categoryId, now);
        }
        return CategoryChangeRateItem.withBefore(categoryId, now, before);
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
     * 텔레그램 캡션용 카테고리 TOP2 랭킹 — 대분류(depth 0)만, 기본 제외 구간(market_value_tier_threshold
     * .is_excluded_by_default)을 뺀 **beforeMinutes분 전 대비 변화(%p)** 기준 내림차순 TOP2.
     * 필터·정렬·TOP2 확정까지 전부 여기서 끝내고, notification 쪽(CategoryRankingTextBuilder)은 텍스트
     * 포매팅만 한다. 데이터 없는 마켓은 getCategoryChangeRates가 이미 결과에서 뺀 상태라 자동으로
     * 여기서도 빠진다.
     *
     * <p>before가 없는 카테고리는 순위에서 빠지므로, beforeMinutes 시각에 스냅샷이 통째로 없으면(장
     * 시작 직후 첫 발송이 매일 여기 걸린다 — 08:10 발송의 before는 07:55인데 수집은 08:00부터다)
     * topCategories가 빈 목록이 된다. 그 처리는 호출부(CategoryRankingTextBuilder)가 한다.
     */
    public List<CategoryRankingSummary> getTopCategoryRankings(
            MarketQuery marketQuery,
            LocalDateTime snapshotTime,
            int beforeMinutes,
            AverageMode averageMode,
            boolean sectorFilter) {
        SnapshotResponse<CategoryChangeRateMarketRanking> ranking =
                getCategoryChangeRates(marketQuery, snapshotTime, beforeMinutes);
        Set<Long> excludedTierIds = excludedTierIds();
        Map<Long, MarketMapCategory> categoryById = findCategoryById();
        return ranking.items().stream()
                .map(marketRanking -> toCategoryRankingSummary(
                        marketRanking, excludedTierIds, averageMode, sectorFilter, categoryById))
                .toList();
    }

    /**
     * 텔레그램 캡션용 카테고리 TOP2 랭킹 — 위와 같은 필터·TOP2 규칙이되 **등락률(now, %) 기준**, 마켓별.
     * 매일 첫 발송(전날 대비 before가 없는 tick)의 변화율 폴백 전용이다. getCategoryChangeRates가
     * before도 항상 같이 조회하지만 여기서는 now만 쓰고 버린다 — docs/backlog.md의 「텔레그램 경로가
     * before를 조회하고 버린다」 항목, 이번에 고치지 않는다.
     */
    public List<CategoryRankingSummary> getTopCategoryRankingsByChangeRate(
            MarketQuery marketQuery,
            LocalDateTime snapshotTime,
            int beforeMinutes,
            AverageMode averageMode,
            boolean sectorFilter) {
        SnapshotResponse<CategoryChangeRateMarketRanking> ranking =
                getCategoryChangeRates(marketQuery, snapshotTime, beforeMinutes);
        Set<Long> excludedTierIds = excludedTierIds();
        Map<Long, MarketMapCategory> categoryById = findCategoryById();
        return ranking.items().stream()
                .map(marketRanking -> toCategoryRankingSummaryByChangeRate(
                        marketRanking, excludedTierIds, averageMode, sectorFilter, categoryById))
                .toList();
    }

    /**
     * 맵 발송(코스피+코스닥 앨범) 캡션용 — 두 마켓의 카테고리별 breakdown을 먼저 합친 뒤 가중평균을 낸
     * **등락률(now, %) 기준** TOP2. 마켓 구분이 없어 반환 타입도 마켓별 랭킹(CategoryRankingSummary)이
     * 아니라 List&lt;TopCategoryItem&gt; 하나다. buildCustomMarketMap의 All Stocks 병합과 같은 패턴
     * (카테고리별 breakdown을 합친 뒤 한 번만 나눈다 — 이미 나뉜 평균끼리 다시 평균내면 틀린다)이지만,
     * 화면 트리 없이 랭킹만 필요해서 별도로 조립한다.
     *
     * <p>캡션 전용이다. 이 결과로 "어느 마켓을 캡처할지"를 정하면 안 된다 — 맵 페이지는
     * sector_price_snapshot으로 그려지는데 여기는 카테고리 등락률 스냅샷을 보므로, 등락률 수집만
     * 실패한 tick에서는 맵이 멀쩡히 그려지는데도 빈 목록이 나온다. 그 시각 스냅샷이 통째로 없으면
     * 빈 목록을 돌려주므로, 호출부가 캡션을 붙일지 말지 판단한다.
     */
    public List<TopCategoryItem> getMergedTopCategoryRanking(
            MarketQuery marketQuery, LocalDateTime snapshotTime, AverageMode averageMode, boolean sectorFilter) {
        List<Market> markets = marketQuery.toMarkets();
        List<Map<Long, List<CategoryTierBreakdown>>> breakdownsByMarket = markets.stream()
                .map(market -> categoryTierAggregationService.aggregateByCategory(
                        getCustomMarketMapTree(market, snapshotTime)))
                .toList();
        // 결정 2 — 요청한 마켓 중 하나라도 합산 결과가 비면 빈 목록을 돌려준다. 한 마켓만 수집에
        // 실패해도 나머지 마켓만으로 TOP2를 뽑으면, 지도 이미지는 markets 전체 기준인데 캡션은 일부
        // 마켓 기준이 되어 틀린 캡션이 조용히 나간다.
        if (breakdownsByMarket.stream().anyMatch(Map::isEmpty)) {
            return List.of();
        }
        Map<Long, List<CategoryTierBreakdown>> mergedByCategoryId = breakdownsByMarket.stream()
                .flatMap(byCategoryId -> byCategoryId.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                    List<CategoryTierBreakdown> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                }));

        Map<Long, MarketMapCategory> categoryById = findCategoryById();
        Set<Long> excludedTierIds = excludedTierIds();

        return mergedByCategoryId.entrySet().stream()
                .filter(entry -> categoryById.containsKey(entry.getKey()))
                .filter(entry -> categoryById.get(entry.getKey()).getDepth() == 0)
                .filter(entry -> isSectorIncluded(entry.getKey(), sectorFilter, categoryById))
                .map(entry -> toTopCategoryItemByChangeRate(
                        categoryById.get(entry.getKey()).getName(), entry.getValue(), excludedTierIds, averageMode))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TopCategoryItem::changeRate).reversed())
                .limit(TOP_N)
                .toList();
    }

    private Map<Long, MarketMapCategory> findCategoryById() {
        return marketMapCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MarketMapCategory::getId, Function.identity()));
    }

    /** sectorFilter가 꺼져 있으면 전부 포함. 켜져 있으면 그 카테고리의 isExcluded만 본다(결정 4 —
     * getCategoryChangeRates가 아니라 TOP2 경로에만 거는 필터). 세 호출부 모두 categoryId가
     * categoryById에 이미 존재함을 보장한 뒤 부른다(getMergedTopCategoryRanking은 앞선 containsKey
     * 필터, 나머지 둘은 decorateRanking이 이미 걸러냄) — category가 null인 경로는 현재 없다. */
    private boolean isSectorIncluded(Long categoryId, boolean sectorFilter, Map<Long, MarketMapCategory> categoryById) {
        if (!sectorFilter) {
            return true;
        }
        MarketMapCategory category = categoryById.get(categoryId);
        return category != null && !category.isExcluded();
    }

    private Set<Long> excludedTierIds() {
        return marketValueTierThresholdService.getValueTiers().stream()
                .filter(MarketValueTierItem::isExcludedByDefault)
                .map(MarketValueTierItem::id)
                .collect(Collectors.toSet());
    }

    private CategoryRankingSummary toCategoryRankingSummary(
            CategoryChangeRateMarketRanking marketRanking,
            Set<Long> excludedTierIds,
            AverageMode averageMode,
            boolean sectorFilter,
            Map<Long, MarketMapCategory> categoryById) {
        List<TopCategoryItem> topCategories = marketRanking.items().stream()
                .filter(item -> item.depth() == 0)
                .filter(item -> isSectorIncluded(item.categoryId(), sectorFilter, categoryById))
                .map(item -> toTopCategoryItem(item, excludedTierIds, averageMode))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TopCategoryItem::changeRate).reversed())
                .limit(TOP_N)
                .toList();
        // 지수는 캡션 본문과 달리 현재 등락률 그대로다 — 이 값을 쓰는 buildRankingText 쪽 서식이
        // "#코스피 +x.xx%"라 델타가 아니라 현재값을 기대한다.
        BigDecimal indexChangeRate =
                marketRanking.index() != null ? marketRanking.index().now() : null;
        return new CategoryRankingSummary(marketRanking.market(), indexChangeRate, topCategories);
    }

    private CategoryRankingSummary toCategoryRankingSummaryByChangeRate(
            CategoryChangeRateMarketRanking marketRanking,
            Set<Long> excludedTierIds,
            AverageMode averageMode,
            boolean sectorFilter,
            Map<Long, MarketMapCategory> categoryById) {
        List<TopCategoryItem> topCategories = marketRanking.items().stream()
                .filter(item -> item.depth() == 0)
                .filter(item -> isSectorIncluded(item.categoryId(), sectorFilter, categoryById))
                .map(item ->
                        toTopCategoryItemByChangeRate(item.categoryName(), item.now(), excludedTierIds, averageMode))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TopCategoryItem::changeRate).reversed())
                .limit(TOP_N)
                .toList();
        BigDecimal indexChangeRate =
                marketRanking.index() != null ? marketRanking.index().now() : null;
        return new CategoryRankingSummary(marketRanking.market(), indexChangeRate, topCategories);
    }

    /**
     * 캡션이 "N분 전 대비"라 now와 before의 가중평균 차이(%p)를 담는다. before가 없으면 null을 돌려
     * 순위에서 빠진다 — 없는 것을 0으로 치면 now가 그대로 델타가 되어 조용히 틀린 값이 1위로 올라온다.
     * combine()이 빈 목록에 0을 돌려주므로 빈 목록도 같이 걸러야 한다.
     */
    private TopCategoryItem toTopCategoryItem(
            CategoryChangeRateItem item, Set<Long> excludedTierIds, AverageMode averageMode) {
        if (item.before() == null) {
            return null;
        }
        BigDecimal now = avgOf(item.now(), excludedTierIds, averageMode);
        BigDecimal before = avgOf(item.before(), excludedTierIds, averageMode);
        if (now == null || before == null) {
            return null;
        }
        return new TopCategoryItem(item.categoryName(), now.subtract(before));
    }

    /** 등락률(now) 기준 TOP2용 — before 없이 now의 평균 하나만 담는다. */
    private TopCategoryItem toTopCategoryItemByChangeRate(
            String categoryName, List<CategoryTierBreakdown> now, Set<Long> excludedTierIds, AverageMode averageMode) {
        BigDecimal avg = avgOf(now, excludedTierIds, averageMode);
        if (avg == null) {
            return null;
        }
        return new TopCategoryItem(categoryName, avg);
    }

    private BigDecimal avgOf(
            List<CategoryTierBreakdown> breakdowns, Set<Long> excludedTierIds, AverageMode averageMode) {
        List<CategoryTierBreakdown> included = breakdowns.stream()
                .filter(breakdown -> !excludedTierIds.contains(breakdown.tierId()))
                .toList();
        if (included.isEmpty()) {
            return null;
        }
        SnapshotAverages averages = categoryTierAggregationService.combine(included);
        return switch (averageMode) {
            case WEIGHTED -> averages.weightedAvgChangeRate();
            case SIMPLE -> averages.simpleAvgChangeRate();
        };
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
