package dev.eolmae.marketmonitor.domain.view.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
import dev.eolmae.marketmonitor.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockAlias;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockAliasRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.custom.service.SectorTierAggregationService;
import dev.eolmae.marketmonitor.domain.notification.properties.MarketMonitorProperties;
import dev.eolmae.marketmonitor.domain.stock.entity.IndustryInfo;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.IndustryInfoRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceCacheService.CachedStockPrice;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.view.dto.MarketIndexChangeRate;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapSectorNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketOverviewItem;
import dev.eolmae.marketmonitor.domain.view.dto.SectorChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.SectorChangeRateMarketRanking;
import dev.eolmae.marketmonitor.domain.view.dto.SectorRankingSummary;
import dev.eolmae.marketmonitor.domain.view.dto.SectorTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.dto.TopSectorItem;
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
import java.util.Optional;
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

    private static final String UNSECTORED = "미분류";
    private static final long NO_PARENT_KEY = 0L;
    /** 기본 마켓맵은 어드민이 구성한 섹터 트리를 안 쓰므로 exclude 판정 대상 자체가 아님 — id는 관례상 0 고정 */
    private static final Long NO_SECTOR_ID = 0L;

    private static final int TOP_N = 2;

    private final StockInfoCacheService stockInfoCacheService;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final SectorPriceCacheService sectorPriceCacheService;
    private final CustomSectorRepository customSectorRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final CustomStockAliasRepository customStockAliasRepository;
    private final SectorTierAggregationService sectorTierAggregationService;
    private final CustomValueTierThresholdService customValueTierThresholdService;
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;
    private final IndustryInfoRepository industryInfoRepository;
    private final MarketMonitorProperties marketMonitorProperties;

    /** 기본 마켓맵: stock_info 섹터 그대로(override 없이) 기준, 자식 없는 1뎁스 노드로 감싸서 반환
     * (getCustomMarketMap과 응답 모양 통일). snapshotTime이 없으면 최신, 있으면 그 시각 그대로(결정 4). */
    public MarketMapResponse getDefaultMarketMap(MarketQuery marketQuery, LocalDateTime snapshotTime) {
        List<Market> markets = marketQuery.toMarkets();
        return resolveSnapshotTime(markets, snapshotTime)
                .map(resolvedSnapshotTime -> buildDefaultMarketMap(markets, resolvedSnapshotTime))
                .orElseGet(MarketMapResponse::empty);
    }

    private MarketMapResponse buildDefaultMarketMap(List<Market> markets, LocalDateTime latestSnapshotTime) {
        List<StockInfo> candidates = filterCandidates(markets);
        Map<String, CachedStockPrice> priceMap = findPriceByStockCode(markets, latestSnapshotTime);
        List<CustomValueTierThreshold> sortedTiers = customValueTierThresholdService.findDefaultSortedAscending();
        Set<Long> industryIds = candidates.stream()
                .map(StockInfo::getIndustryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> industryNameById = industryIds.isEmpty()
                ? Map.of()
                : industryInfoRepository.findAllById(industryIds).stream()
                        .collect(Collectors.toMap(IndustryInfo::getId, IndustryInfo::getName));

        Map<String, List<MarketMapItem>> grouped = candidates.stream()
                .filter(stockInfo -> priceMap.containsKey(stockInfo.getStockCode()))
                .collect(Collectors.groupingBy(
                        stockInfo -> normalizeSectorName(industryName(stockInfo, industryNameById)),
                        Collectors.mapping(
                                stockInfo ->
                                        toMarketMapItem(stockInfo, priceMap.get(stockInfo.getStockCode()), sortedTiers),
                                Collectors.toList())));

        List<MarketMapSectorNode> nodes = grouped.entrySet().stream()
                .map(entry -> {
                    List<MarketMapItem> items = entry.getValue();
                    BigDecimal totalMarketValue = items.stream()
                            .map(MarketMapItem::totalMarketValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return MarketMapSectorNode.leaf(NO_SECTOR_ID, entry.getKey(), totalMarketValue, items);
                })
                .toList();

        return new MarketMapResponse(latestSnapshotTime, nodes, findSingleMarketOverview(markets, latestSnapshotTime));
    }

    /** 커스텀 마켓맵: 어드민이 구성한 섹터 트리 기준. 트리에 배정 안 된 종목은 stock_info 섹터로
     * 묶은 노드를 같은 레벨에 섞어서 반환. snapshotTime이 없으면 최신, 있으면 그 시각 그대로(결정 4). */
    public MarketMapResponse getCustomMarketMap(MarketQuery marketQuery, LocalDateTime snapshotTime) {
        Long userId = CurrentUser.requireId();
        List<Market> markets = marketQuery.toMarkets();
        return resolveSnapshotTime(markets, snapshotTime)
                .map(resolvedSnapshotTime -> buildCustomMarketMap(markets, resolvedSnapshotTime, userId))
                .orElseGet(MarketMapResponse::empty);
    }

    /** snapshotTime이 없으면 지금처럼 markets 전부가 공통으로 가진 최신 시각을 쓴다(그 정의상 이미
     * 전부에 있는 시각이다). 있으면 그 시각을 그대로 쓰되, markets 전부에 정확히 그 시각이 있을 때만
     * 유효하다 — 하나라도 없으면 빈 응답이다. 가까운 시각으로 대체하지 않는다(결정 4). */
    private Optional<LocalDateTime> resolveSnapshotTime(List<Market> markets, LocalDateTime snapshotTime) {
        if (snapshotTime == null) {
            return sectorPriceSnapshotService.findLatestCommonSnapshotTime(markets);
        }
        boolean allMarketsHaveSnapshot =
                markets.stream().allMatch(market -> sectorPriceSnapshotService.existsSnapshot(market, snapshotTime));
        return allMarketsHaveSnapshot ? Optional.of(snapshotTime) : Optional.empty();
    }

    /**
     * 마켓맵 섹터별 등락률 랭킹 — 텔레그램 발송 경로처럼 이미 확정된 dataTime을 그대로
     * 써야 하는 호출부(getTopSectorRankings)용이라 시각을 인자로 받는다. 그 시각에 지수 스냅샷이
     * 없으면(부분 실패로 아예 없는 경우) 조용히 비워서 내려준다 — 다른 시점 값으로 대체하지 않는다.
     */
    public SnapshotResponse<SectorChangeRateMarketRanking> getSectorChangeRates(
            MarketQuery marketQuery, LocalDateTime snapshotTime, int beforeMinutes) {
        List<Market> markets = marketQuery.toMarkets();
        SnapshotResponse<SectorChangeRateMarketRanking> ranking =
                buildRankingForMarkets(markets, snapshotTime, beforeMinutes);

        Map<Market, BigDecimal> nowIndexChangeRateByMarket =
                toChangeRateByMarket(findOverviewsBySnapshotTime(snapshotTime));
        Map<Market, BigDecimal> beforeIndexChangeRateByMarket =
                toChangeRateByMarket(findOverviewsBySnapshotTime(snapshotTime.minusMinutes(beforeMinutes)));
        Map<Long, CustomSector> sectorById = findSectorById(customDataUserId());

        return new SnapshotResponse<>(
                snapshotTime,
                ranking.items().stream()
                        .map(marketRanking -> decorateRanking(
                                marketRanking, nowIndexChangeRateByMarket, beforeIndexChangeRateByMarket, sectorById))
                        .toList());
    }

    /**
     * markets가 정확히 snapshotTime 시각에 가진 섹터별 현재/직전(beforeMinutes분 전) 등락률 랭킹 —
     * 마켓마다 그 시각의 커스텀 트리를 빌드해 합산한다(결정 1). 합산 결과(섹터별 구간 원시 합계)가
     * 빈 마켓은 결과 목록에서 아예 빠진다 — 트리 자체가 비어 있지 않아도(섹터 노드는 있는데 가격
     * 행이 없어 종목이 0개) 합산 결과는 빌 수 있으므로, 트리가 아니라 합산 결과의 비어 있음으로
     * 판단한다. beforeMinutes 시각에 정확히 일치하는 섹터가 없으면(장 시작 직후, 수집 gap 등) 해당
     * 섹터는 before 없이 내려준다 — 가장 가까운 다른 시점 데이터로 조용히 대체하지 않는다.
     */
    private SnapshotResponse<SectorChangeRateMarketRanking> buildRankingForMarkets(
            List<Market> markets, LocalDateTime snapshotTime, int beforeMinutes) {
        LocalDateTime beforeTime = snapshotTime.minusMinutes(beforeMinutes);
        List<SectorChangeRateMarketRanking> rankings = markets.stream()
                .map(market -> toMarketRanking(market, snapshotTime, beforeTime))
                .filter(Objects::nonNull)
                .toList();
        return new SnapshotResponse<>(snapshotTime, rankings);
    }

    private SectorChangeRateMarketRanking toMarketRanking(
            Market market, LocalDateTime snapshotTime, LocalDateTime beforeTime) {
        Map<Long, List<SectorTierBreakdown>> now =
                sectorTierAggregationService.aggregateBySector(getCustomMarketMapTree(market, snapshotTime));
        if (now.isEmpty()) {
            return null;
        }
        Map<Long, List<SectorTierBreakdown>> before =
                sectorTierAggregationService.aggregateBySector(getCustomMarketMapTree(market, beforeTime));
        List<SectorChangeRateItem> items = now.entrySet().stream()
                .map(entry -> toItem(entry.getKey(), entry.getValue(), before.get(entry.getKey())))
                .toList();
        return new SectorChangeRateMarketRanking(market, items);
    }

    private SectorChangeRateItem toItem(
            Long sectorId, List<SectorTierBreakdown> now, List<SectorTierBreakdown> before) {
        if (before == null) {
            return SectorChangeRateItem.withoutBefore(sectorId, now);
        }
        return SectorChangeRateItem.withBefore(sectorId, now, before);
    }

    private Map<Market, BigDecimal> toChangeRateByMarket(Map<Market, MarketOverviewSnapshot> overviewsByMarket) {
        return overviewsByMarket.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().getChangeRate()));
    }

    private SectorChangeRateMarketRanking decorateRanking(
            SectorChangeRateMarketRanking marketRanking,
            Map<Market, BigDecimal> nowIndexChangeRateByMarket,
            Map<Market, BigDecimal> beforeIndexChangeRateByMarket,
            Map<Long, CustomSector> sectorById) {
        // sectorId는 항상 buildSectorTree가 그 시각 현재 섹터 테이블을 순회하며 만든 것이라
        // sectorById에 없는 id가 나올 수 없다 — 방어적으로 걸러둔다. 잘못된 depth를 채워 넣지 않는다
        // (대분류 판정에 영향을 준다).
        List<SectorChangeRateItem> items = marketRanking.items().stream()
                .filter(item -> sectorById.containsKey(item.sectorId()))
                .map(item -> decorateWithSector(item, sectorById))
                .toList();
        MarketIndexChangeRate index = toMarketIndexChangeRate(
                marketRanking.market(), nowIndexChangeRateByMarket, beforeIndexChangeRateByMarket);
        return new SectorChangeRateMarketRanking(marketRanking.market(), items, index);
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

    private SectorChangeRateItem decorateWithSector(SectorChangeRateItem item, Map<Long, CustomSector> sectorById) {
        CustomSector sector = sectorById.get(item.sectorId());
        return item.withSector(sector.getName(), sector.getDepth());
    }

    /**
     * 텔레그램 캡션용 섹터 TOP2 랭킹 — 대분류(depth 0)만, 기본 제외 구간(custom_value_tier_threshold
     * .is_excluded_by_default)을 뺀 **beforeMinutes분 전 대비 변화(%p)** 기준 내림차순 TOP2.
     * 필터·정렬·TOP2 확정까지 전부 여기서 끝내고, notification 쪽(SectorRankingTextBuilder)은 텍스트
     * 포매팅만 한다. 데이터 없는 마켓은 getSectorChangeRates가 이미 결과에서 뺀 상태라 자동으로
     * 여기서도 빠진다.
     *
     * <p>before가 없는 섹터는 순위에서 빠지므로, beforeMinutes 시각에 스냅샷이 통째로 없으면(장
     * 시작 직후 첫 발송이 매일 여기 걸린다 — 08:10 발송의 before는 07:55인데 수집은 08:00부터다)
     * topSectors가 빈 목록이 된다. 그 처리는 호출부(SectorRankingTextBuilder)가 한다.
     */
    public List<SectorRankingSummary> getTopSectorRankings(
            MarketQuery marketQuery,
            LocalDateTime snapshotTime,
            int beforeMinutes,
            AverageMode averageMode,
            boolean sectorFilter) {
        SnapshotResponse<SectorChangeRateMarketRanking> ranking =
                getSectorChangeRates(marketQuery, snapshotTime, beforeMinutes);
        Long userId = customDataUserId();
        Set<Long> excludedTierIds = excludedTierIds(userId);
        Map<Long, CustomSector> sectorById = findSectorById(userId);
        return ranking.items().stream()
                .map(marketRanking ->
                        toSectorRankingSummary(marketRanking, excludedTierIds, averageMode, sectorFilter, sectorById))
                .toList();
    }

    /**
     * 텔레그램 캡션용 섹터 TOP2 랭킹 — 위와 같은 필터·TOP2 규칙이되 **등락률(now, %) 기준**, 마켓별.
     * 매일 첫 발송(전날 대비 before가 없는 tick)의 변화율 폴백 전용이다. getSectorChangeRates가
     * before도 항상 같이 조회하지만 여기서는 now만 쓰고 버린다 — docs/backlog.md의 「텔레그램 경로가
     * before를 조회하고 버린다」 항목, 이번에 고치지 않는다.
     */
    public List<SectorRankingSummary> getTopSectorRankingsByChangeRate(
            MarketQuery marketQuery,
            LocalDateTime snapshotTime,
            int beforeMinutes,
            AverageMode averageMode,
            boolean sectorFilter) {
        SnapshotResponse<SectorChangeRateMarketRanking> ranking =
                getSectorChangeRates(marketQuery, snapshotTime, beforeMinutes);
        Long userId = customDataUserId();
        Set<Long> excludedTierIds = excludedTierIds(userId);
        Map<Long, CustomSector> sectorById = findSectorById(userId);
        return ranking.items().stream()
                .map(marketRanking -> toSectorRankingSummaryByChangeRate(
                        marketRanking, excludedTierIds, averageMode, sectorFilter, sectorById))
                .toList();
    }

    /**
     * 맵 발송(코스피+코스닥 앨범) 캡션용 — 두 마켓의 섹터별 breakdown을 먼저 합친 뒤 가중평균을 낸
     * **등락률(now, %) 기준** TOP2. 마켓 구분이 없어 반환 타입도 마켓별 랭킹(SectorRankingSummary)이
     * 아니라 List&lt;TopSectorItem&gt; 하나다. buildCustomMarketMap의 All Stocks 병합과 같은 패턴
     * (섹터별 breakdown을 합친 뒤 한 번만 나눈다 — 이미 나뉜 평균끼리 다시 평균내면 틀린다)이지만,
     * 화면 트리 없이 랭킹만 필요해서 별도로 조립한다.
     *
     * <p>캡션 전용이다. 이 결과로 "어느 마켓을 캡처할지"를 정하면 안 된다 — 맵과 캡션이 같은 가격
     * 행(sector_price_snapshot)을 쓰더라도, 한 마켓만 그 시각 가격 행이 없으면 이 메서드는 "하나라도
     * 비면 빈 목록" 규칙에 걸려 통째로 비는 반면 맵은 나머지 마켓만으로도 그려진다. 그 시각 가격 행이
     * 통째로 없거나 요청 마켓 중 하나라도 합산이 비면 빈 목록을 돌려주므로, 호출부가 캡션을 붙일지
     * 말지 판단한다.
     */
    public List<TopSectorItem> getMergedTopSectorRanking(
            MarketQuery marketQuery, LocalDateTime snapshotTime, AverageMode averageMode, boolean sectorFilter) {
        List<Market> markets = marketQuery.toMarkets();
        List<Map<Long, List<SectorTierBreakdown>>> breakdownsByMarket = markets.stream()
                .map(market ->
                        sectorTierAggregationService.aggregateBySector(getCustomMarketMapTree(market, snapshotTime)))
                .toList();
        // 결정 2 — 요청한 마켓 중 하나라도 합산 결과가 비면 빈 목록을 돌려준다. 한 마켓만 수집에
        // 실패해도 나머지 마켓만으로 TOP2를 뽑으면, 지도 이미지는 markets 전체 기준인데 캡션은 일부
        // 마켓 기준이 되어 틀린 캡션이 조용히 나간다.
        if (breakdownsByMarket.stream().anyMatch(Map::isEmpty)) {
            return List.of();
        }
        Map<Long, List<SectorTierBreakdown>> mergedBySectorId = breakdownsByMarket.stream()
                .flatMap(bySectorId -> bySectorId.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
                    List<SectorTierBreakdown> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                }));

        Long userId = customDataUserId();
        Map<Long, CustomSector> sectorById = findSectorById(userId);
        Set<Long> excludedTierIds = excludedTierIds(userId);

        return mergedBySectorId.entrySet().stream()
                .filter(entry -> sectorById.containsKey(entry.getKey()))
                .filter(entry -> sectorById.get(entry.getKey()).getDepth() == 0)
                .filter(entry -> isSectorIncluded(entry.getKey(), sectorFilter, sectorById))
                .map(entry -> toTopSectorItemByChangeRate(
                        sectorById.get(entry.getKey()).getName(), entry.getValue(), excludedTierIds, averageMode))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TopSectorItem::changeRate).reversed())
                .limit(TOP_N)
                .toList();
    }

    private Map<Long, CustomSector> findSectorById(Long userId) {
        return customSectorRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(CustomSector::getId, Function.identity()));
    }

    /** sectorFilter가 꺼져 있으면 전부 포함. 켜져 있으면 그 섹터의 isExcluded만 본다(결정 4 —
     * getSectorChangeRates가 아니라 TOP2 경로에만 거는 필터). 세 호출부 모두 sectorId가
     * sectorById에 이미 존재함을 보장한 뒤 부른다(getMergedTopSectorRanking은 앞선 containsKey
     * 필터, 나머지 둘은 decorateRanking이 이미 걸러냄) — sector가 null인 경로는 현재 없다. */
    private boolean isSectorIncluded(Long sectorId, boolean sectorFilter, Map<Long, CustomSector> sectorById) {
        if (!sectorFilter) {
            return true;
        }
        CustomSector sector = sectorById.get(sectorId);
        return sector != null && !sector.isExcluded();
    }

    private Set<Long> excludedTierIds(Long userId) {
        return customValueTierThresholdService.getValueTiers(userId).stream()
                .filter(CustomValueTierItem::isExcludedByDefault)
                .map(CustomValueTierItem::id)
                .collect(Collectors.toSet());
    }

    private SectorRankingSummary toSectorRankingSummary(
            SectorChangeRateMarketRanking marketRanking,
            Set<Long> excludedTierIds,
            AverageMode averageMode,
            boolean sectorFilter,
            Map<Long, CustomSector> sectorById) {
        List<TopSectorItem> topSectors = marketRanking.items().stream()
                .filter(item -> item.depth() == 0)
                .filter(item -> isSectorIncluded(item.sectorId(), sectorFilter, sectorById))
                .map(item -> toTopSectorItem(item, excludedTierIds, averageMode))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TopSectorItem::changeRate).reversed())
                .limit(TOP_N)
                .toList();
        // 지수는 캡션 본문과 달리 현재 등락률 그대로다 — 이 값을 쓰는 buildRankingText 쪽 서식이
        // "#코스피 +x.xx%"라 델타가 아니라 현재값을 기대한다.
        BigDecimal indexChangeRate =
                marketRanking.index() != null ? marketRanking.index().now() : null;
        return new SectorRankingSummary(marketRanking.market(), indexChangeRate, topSectors);
    }

    private SectorRankingSummary toSectorRankingSummaryByChangeRate(
            SectorChangeRateMarketRanking marketRanking,
            Set<Long> excludedTierIds,
            AverageMode averageMode,
            boolean sectorFilter,
            Map<Long, CustomSector> sectorById) {
        List<TopSectorItem> topSectors = marketRanking.items().stream()
                .filter(item -> item.depth() == 0)
                .filter(item -> isSectorIncluded(item.sectorId(), sectorFilter, sectorById))
                .map(item -> toTopSectorItemByChangeRate(item.sectorName(), item.now(), excludedTierIds, averageMode))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TopSectorItem::changeRate).reversed())
                .limit(TOP_N)
                .toList();
        BigDecimal indexChangeRate =
                marketRanking.index() != null ? marketRanking.index().now() : null;
        return new SectorRankingSummary(marketRanking.market(), indexChangeRate, topSectors);
    }

    /**
     * 캡션이 "N분 전 대비"라 now와 before의 가중평균 차이(%p)를 담는다. before가 없으면 null을 돌려
     * 순위에서 빠진다 — 없는 것을 0으로 치면 now가 그대로 델타가 되어 조용히 틀린 값이 1위로 올라온다.
     * combine()이 빈 목록에 0을 돌려주므로 빈 목록도 같이 걸러야 한다.
     */
    private TopSectorItem toTopSectorItem(
            SectorChangeRateItem item, Set<Long> excludedTierIds, AverageMode averageMode) {
        if (item.before() == null) {
            return null;
        }
        BigDecimal now = avgOf(item.now(), excludedTierIds, averageMode);
        BigDecimal before = avgOf(item.before(), excludedTierIds, averageMode);
        if (now == null || before == null) {
            return null;
        }
        return new TopSectorItem(item.sectorName(), now.subtract(before));
    }

    /** 등락률(now) 기준 TOP2용 — before 없이 now의 평균 하나만 담는다. */
    private TopSectorItem toTopSectorItemByChangeRate(
            String sectorName, List<SectorTierBreakdown> now, Set<Long> excludedTierIds, AverageMode averageMode) {
        BigDecimal avg = avgOf(now, excludedTierIds, averageMode);
        if (avg == null) {
            return null;
        }
        return new TopSectorItem(sectorName, avg);
    }

    private BigDecimal avgOf(List<SectorTierBreakdown> breakdowns, Set<Long> excludedTierIds, AverageMode averageMode) {
        List<SectorTierBreakdown> included = breakdowns.stream()
                .filter(breakdown -> !excludedTierIds.contains(breakdown.tierId()))
                .toList();
        if (included.isEmpty()) {
            return null;
        }
        SnapshotAverages averages = sectorTierAggregationService.combine(included);
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
     * 텔레그램 랭킹 조회(toMarketRanking·getMergedTopSectorRanking)가 그 시각 섹터별 합산을 만들
     * 때 쓰는 원본 트리. 변화율 데코레이션은 필요 없어서(어차피 안 쓰임) buildSectorTree만 노출한다.
     * snapshotTime은 호출부가 이미 들고 있는 값을 그대로 받는다 — "최신 시각"을 다시 조회하면, 특정
     * market 수집이 실패했을 때 예전 시각 데이터를 지금 시각인 것처럼 섞어 쓰게 된다. 대신 정확히 이
     * snapshotTime에 데이터가 없으면 빈 트리를 반환해서 호출부가 그 마켓을 결과에서 빼도록 한다.
     */
    public List<MarketMapSectorNode> getCustomMarketMapTree(Market market, LocalDateTime snapshotTime) {
        if (sectorPriceSnapshotService.notExistsSnapshot(market, snapshotTime)) {
            return List.of();
        }
        return buildSectorTree(List.of(market), snapshotTime, customDataUserId());
    }

    private MarketMapResponse buildCustomMarketMap(
            List<Market> markets, LocalDateTime latestSnapshotTime, Long userId) {
        List<MarketMapSectorNode> tree = buildSectorTree(markets, latestSnapshotTime, userId);
        return new MarketMapResponse(latestSnapshotTime, tree, findSingleMarketOverview(markets, latestSnapshotTime));
    }

    private List<MarketMapSectorNode> buildSectorTree(
            List<Market> markets, LocalDateTime latestSnapshotTime, Long userId) {
        List<StockInfo> candidates = filterCandidates(markets);
        List<CustomSector> sectors = customSectorRepository.findAllByUserId(userId);
        Map<Long, List<CustomSector>> childrenByParentId = new HashMap<>();
        for (CustomSector sector : sectors) {
            Long parentKey = sector.hasNoParent() ? NO_PARENT_KEY : sector.getParentId();
            childrenByParentId
                    .computeIfAbsent(parentKey, key -> new ArrayList<>())
                    .add(sector);
        }
        Map<String, CustomStockSector> stockSectorMap = findStockSectorMap(userId);
        Map<String, String> aliasByStockCode = customStockAliasRepository.findAllByIdUserId(userId).stream()
                .collect(Collectors.toMap(CustomStockAlias::getStockCode, CustomStockAlias::getAlias));
        Map<String, CachedStockPrice> priceMap = findPriceByStockCode(markets, latestSnapshotTime);
        List<CustomValueTierThreshold> sortedTiers = customValueTierThresholdService.findAllSortedAscending(userId);

        Map<Long, List<MarketMapItem>> itemsBySectorId = candidates.stream()
                .filter(stockInfo -> stockSectorMap.containsKey(stockInfo.getStockCode()))
                .filter(stockInfo -> priceMap.containsKey(stockInfo.getStockCode()))
                .collect(Collectors.groupingBy(
                        stockInfo ->
                                stockSectorMap.get(stockInfo.getStockCode()).getSectorId(),
                        Collectors.mapping(
                                stockInfo -> toMarketMapItem(
                                        stockInfo,
                                        priceMap.get(stockInfo.getStockCode()),
                                        aliasByStockCode.get(stockInfo.getStockCode()),
                                        sortedTiers),
                                Collectors.toList())));

        return childrenByParentId.getOrDefault(NO_PARENT_KEY, List.of()).stream()
                .map(sector -> toSectorNode(sector, childrenByParentId, itemsBySectorId))
                .toList();
    }

    private MarketMapSectorNode toSectorNode(
            CustomSector sector,
            Map<Long, List<CustomSector>> childrenByParentId,
            Map<Long, List<MarketMapItem>> itemsBySectorId) {
        List<MarketMapSectorNode> children = childrenByParentId.getOrDefault(sector.getId(), List.of()).stream()
                .map(child -> toSectorNode(child, childrenByParentId, itemsBySectorId))
                .toList();
        List<MarketMapItem> items = itemsBySectorId.getOrDefault(sector.getId(), List.of());
        BigDecimal itemsValue =
                items.stream().map(MarketMapItem::totalMarketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal childrenValue =
                children.stream().map(MarketMapSectorNode::totalMarketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MarketMapSectorNode(
                sector.getId(), sector.getName(), sector.isExcluded(), itemsValue.add(childrenValue), children, items);
    }

    /** 마켓별로 SectorPriceCacheService(결정 5)를 불러 합친다. 캐시 키가 (마켓, 시각) 하나 단위라 마켓이
     * 여럿이면 각각 불러야 한다. merge 함수 없는 toMap — findPriceByStockCode와 같다. 마켓 간에 종목코드가
     * 겹칠 수 없으므로(겹치면 데이터 오류) 조용히 덮어쓰지 않고 예외로 드러난다. */
    private Map<String, CachedStockPrice> findPriceByStockCode(List<Market> markets, LocalDateTime snapshotTime) {
        return markets.stream()
                .flatMap(market -> sectorPriceCacheService.getCache(market, snapshotTime).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<StockInfo> filterCandidates(List<Market> markets) {
        return stockInfoCacheService.getCache().values().stream()
                .filter(stockInfo -> markets.contains(stockInfo.getMarketType()))
                .filter(StockInfo::isActiveAndOrdinary)
                .toList();
    }

    private String normalizeSectorName(String sectorName) {
        if (sectorName == null || sectorName.isBlank()) {
            return UNSECTORED;
        }
        return sectorName;
    }

    private String industryName(StockInfo stockInfo, Map<Long, String> industryNameById) {
        if (stockInfo.getIndustryId() == null) {
            return null;
        }
        return industryNameById.get(stockInfo.getIndustryId());
    }

    private Map<String, CustomStockSector> findStockSectorMap(Long userId) {
        return customStockSectorRepository.findAllByIdUserId(userId).stream()
                .collect(Collectors.toMap(CustomStockSector::getStockCode, Function.identity()));
    }

    /** 기본 마켓맵용: alias 없음(커스텀 트리 전용 개념) */
    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo, CachedStockPrice cachedPrice, List<CustomValueTierThreshold> sortedTiers) {
        return toMarketMapItem(stockInfo, cachedPrice, (String) null, sortedTiers);
    }

    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo,
            CachedStockPrice cachedPrice,
            String alias,
            List<CustomValueTierThreshold> sortedTiers) {
        BigDecimal currentPrice = cachedPrice.currentPrice();
        BigDecimal changeRate = cachedPrice.changeRate();
        BigDecimal totalMarketValue = currentPrice.multiply(BigDecimal.valueOf(stockInfo.getListCount()));

        return new MarketMapItem(
                stockInfo.getStockCode(),
                stockInfo.getStockName(),
                alias,
                currentPrice,
                stockInfo.getLastPrice(),
                totalMarketValue,
                customValueTierThresholdService.resolveTier(sortedTiers, totalMarketValue),
                changeRate,
                cachedPrice.snapshotTime());
    }

    private Long customDataUserId() {
        return marketMonitorProperties.userIdOrOwner(CurrentUser.currentId());
    }
}
