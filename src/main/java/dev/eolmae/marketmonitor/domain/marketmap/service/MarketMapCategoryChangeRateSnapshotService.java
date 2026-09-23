package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategoryChangeRateSnapshot;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepositoryCustom.SnapshotRetentionSummary;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketValueTierThresholdRepository;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateMarketRanking;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마켓맵 카테고리별(하위 카테고리 재귀 포함) × 시가총액 구간별 등락률 원시 합계(분자/분모) 스냅샷 저장.
 * 합산 자체는 CategoryTierAggregationService가 하고, 이 서비스는 그 결과를 엔티티로 바꿔 저장만 한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketMapCategoryChangeRateSnapshotService {

    private final CategoryTierAggregationService categoryTierAggregationService;

    // collect.*와 무관한 별개 상수 — "보존할 스냅샷 시각"의 윈도우다. KRX 정규장은 15:30에 닫히고 NXT
    // 애프터마켓은 15:40에 열려서 그 10분은 두 시장 다 닫혀 있어 가격이 안 바뀐다. 그 안에서 가장 늦은
    // 시각(보통 15:35, 종가 동시호가까지 다 반영되는 시각)을 남긴다 — 15:30을 그대로 박으면 동시호가
    // 결과가 아직 다 반영되지 않은 값을 종가로 오인해 남기게 된다.
    private static final LocalTime RETENTION_WINDOW_START = LocalTime.of(15, 30);
    private static final LocalTime RETENTION_WINDOW_END = LocalTime.of(15, 40);
    private static final int RETENTION_LOG_SAMPLE_SIZE = 5;

    private final MarketMapCategoryChangeRateSnapshotRepository marketMapCategoryChangeRateSnapshotRepository;
    private final MarketValueTierThresholdRepository marketValueTierThresholdRepository;

    @Transactional
    public void captureSnapshot(Market market, LocalDateTime snapshotTime, List<MarketMapCategoryNode> tree) {
        if (tree.isEmpty()) {
            return;
        }
        Map<Long, List<CategoryTierBreakdown>> breakdownsByCategoryId =
                categoryTierAggregationService.aggregateByCategory(tree);
        List<MarketMapCategoryChangeRateSnapshot> snapshots = breakdownsByCategoryId.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(breakdown -> MarketMapCategoryChangeRateSnapshot.create(
                                market,
                                entry.getKey(),
                                breakdown.tierId(),
                                snapshotTime,
                                breakdown.weightedSum(),
                                breakdown.totalValue(),
                                breakdown.simpleSum(),
                                breakdown.itemCount())))
                .toList();
        marketMapCategoryChangeRateSnapshotRepository.saveAll(snapshots);
    }

    /** markets 전부가 공통으로 가진 최신 스냅샷 시각 — 호출부(MarketMapQueryService)가 이 시각을 받아
     * 아래 findRankingForMarkets에 그대로 넘긴다. 시각이 없으면(수집 전, 공통 시각 부재) 비어 있다. */
    public Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets) {
        return marketMapCategoryChangeRateSnapshotRepository.findLatestCommonSnapshotTime(markets);
    }

    /**
     * markets가 정확히 snapshotTime 시각에 가진 카테고리별 현재/직전(beforeMinutes분 전) 등락률 랭킹.
     * 호출부가 이미 알고 있는 정확한 snapshotTime을 받는다 — 라이브 조회든 텔레그램 캡션이든 시각은
     * 호출부가 정해서 넘긴다.
     * 그 시각에 데이터가 없는 마켓은 결과 목록에서 아예 빠진다 — 부분적으로만 데이터가 있어도 있는
     * 마켓만으로 랭킹을 구성할 수 있다. beforeMinutes 시각에 정확히 일치하는 스냅샷이 없으면(장 시작
     * 직후, 수집 gap 등) 해당 카테고리는 before 없이 내려준다 — 가장 가까운 다른 시점 데이터로 조용히
     * 대체하지 않는다.
     */
    public SnapshotResponse<CategoryChangeRateMarketRanking> findRankingForMarkets(
            List<Market> markets, LocalDateTime snapshotTime, int beforeMinutes) {
        LocalDateTime beforeTime = snapshotTime.minusMinutes(beforeMinutes);
        Map<Market, Map<Long, List<CategoryTierBreakdown>>> nowByMarket =
                findTierBreakdownsByCategoryId(markets, snapshotTime);
        Map<Market, Map<Long, List<CategoryTierBreakdown>>> beforeByMarket =
                findTierBreakdownsByCategoryId(markets, beforeTime);

        List<CategoryChangeRateMarketRanking> rankings = markets.stream()
                .filter(nowByMarket::containsKey)
                .map(market -> {
                    Map<Long, List<CategoryTierBreakdown>> now = nowByMarket.get(market);
                    Map<Long, List<CategoryTierBreakdown>> before = beforeByMarket.getOrDefault(market, Map.of());
                    List<CategoryChangeRateItem> items = now.entrySet().stream()
                            .map(entry -> toItem(entry.getKey(), entry.getValue(), before.get(entry.getKey())))
                            .toList();
                    return new CategoryChangeRateMarketRanking(market, items);
                })
                .toList();
        return new SnapshotResponse<>(snapshotTime, rankings);
    }

    /** markets가 정확히 snapshotTime 시각에 가진, 카테고리별(하위 재귀 포함) 시가총액 구간별 등락률 원시
     * 합계를 market → categoryId 순으로 묶어 반환한다. markets를 한 번의 IN 쿼리로 조회하므로 마켓이
     * 하나든 여럿이든 쿼리는 항상 1번. 필터(어떤 구간을 포함할지)는 호출부(화면)가 알고 있으므로 여기서는
     * 합산/나눗셈 없이 원시값 그대로 내려준다. 그 시각에 스냅샷이 없는 마켓/카테고리는 결과 맵에 아예
     * 없음(빈 리스트로 채우지 않음). */
    public Map<Market, Map<Long, List<CategoryTierBreakdown>>> findTierBreakdownsByCategoryId(
            List<Market> markets, LocalDateTime snapshotTime) {
        Map<Long, MarketValueTierThreshold> tierById = marketValueTierThresholdRepository.findAll().stream()
                .collect(Collectors.toMap(MarketValueTierThreshold::getId, Function.identity()));
        return marketMapCategoryChangeRateSnapshotRepository
                .findByMarketTypeInAndSnapshotTime(markets, snapshotTime)
                .stream()
                .collect(Collectors.groupingBy(
                        MarketMapCategoryChangeRateSnapshot::getMarketType,
                        Collectors.groupingBy(
                                MarketMapCategoryChangeRateSnapshot::getCategoryId,
                                Collectors.mapping(row -> toBreakdown(row, tierById), Collectors.toList()))));
    }

    private CategoryTierBreakdown toBreakdown(
            MarketMapCategoryChangeRateSnapshot row, Map<Long, MarketValueTierThreshold> tierById) {
        MarketValueTierThreshold tier = tierById.get(row.getMarketValueTierId());
        return new CategoryTierBreakdown(
                tier.getId(),
                tier.getLabel(),
                row.getWeightedSum(),
                row.getTotalValue(),
                row.getSimpleSum(),
                row.getItemCount());
    }

    private CategoryChangeRateItem toItem(
            Long categoryId, List<CategoryTierBreakdown> now, List<CategoryTierBreakdown> before) {
        if (before == null) {
            return CategoryChangeRateItem.withoutBefore(categoryId, now);
        }
        return CategoryChangeRateItem.withBefore(categoryId, now, before);
    }

    /** cutoff 이전이면서 그 날짜·마켓의 보존 윈도우([15:30, 15:40)) latest가 아닌 스냅샷 정리 — dryRun이면
     * 조회만 하고 로그로 남긴다. */
    @Transactional
    public void cleanupSnapshotsBefore(LocalDateTime cutoff, boolean dryRun) {
        List<MarketSnapshotTime> candidatesInWindow =
                marketMapCategoryChangeRateSnapshotRepository.findMarketSnapshotTimesInWindow(
                        cutoff, RETENTION_WINDOW_START, RETENTION_WINDOW_END);
        List<MarketSnapshotTime> retainedSnapshotTimes =
                selectRetainedSnapshotTimes(candidatesInWindow, RETENTION_WINDOW_START, RETENTION_WINDOW_END);

        SnapshotRetentionSummary summary = marketMapCategoryChangeRateSnapshotRepository.summarizeSnapshotsToDelete(
                cutoff, retainedSnapshotTimes, RETENTION_LOG_SAMPLE_SIZE);
        log.info(
                "[카테고리등락률스냅샷정리] 대상건수:{} | cutoff이전전체건수:{} | 최소시각:{} | 최대시각:{} | 표본시각:{}",
                summary.targetCount(),
                summary.totalCountBeforeCutoff(),
                summary.minSnapshotTime(),
                summary.maxSnapshotTime(),
                summary.sampleSnapshotTimes());
        log.info(
                "[카테고리등락률스냅샷정리] 보존시각 | 표본:{} | 보존날짜수:{}",
                summary.retainedSampleSnapshotTimes(),
                summary.retainedDateCount());

        if (dryRun) {
            return;
        }
        requireRetainedSnapshotTimes("카테고리등락률스냅샷정리", retainedSnapshotTimes, summary);

        long deletedCount =
                marketMapCategoryChangeRateSnapshotRepository.deleteSnapshotsBefore(cutoff, retainedSnapshotTimes);
        log.info("[카테고리등락률스냅샷정리] 삭제완료 | 삭제건수:{}", deletedCount);
    }

    /**
     * 보존 목록이 통째로 비었는데 cutoff 이전에 행이 있으면 삭제를 중단한다. 삭제 술어는 "보존 목록에 없는
     * 것"이라 목록이 비면 cutoff 이전 전체가 대상이 된다. 날짜 하나의 윈도우가 빈 것(그날은 전량 삭제가
     * 맞다)과 목록 전체가 빈 것은 다르다 — 후자는 윈도우 상수가 뒤집혔거나 SQL 술어가 틀렸을 때 나오는
     * 모양이고, 이 레포는 DB 테스트가 없어 그 고장이 빌드에서 걸러지지 않는다. 지우고 나서는 되돌릴 수
     * 없으므로 여기서 멈추고 스케줄러가 에스컬레이션하게 둔다.
     */
    private static void requireRetainedSnapshotTimes(
            String taskName, List<MarketSnapshotTime> retainedSnapshotTimes, SnapshotRetentionSummary summary) {
        if (!retainedSnapshotTimes.isEmpty() || summary.totalCountBeforeCutoff() == 0) {
            return;
        }
        throw new IllegalStateException("[%s] 보존할 스냅샷이 하나도 없어 삭제를 중단한다 — cutoff이전전체건수:%d, 삭제대상건수:%d"
                .formatted(taskName, summary.totalCountBeforeCutoff(), summary.targetCount()));
    }

    /** 보존 윈도우 후보를 (마켓, 날짜)로 묶어 각 그룹의 가장 늦은 시각만 남긴다. 윈도우 필터를 SQL(1단계)뿐
     * 아니라 여기서도 다시 거는 이유 — 이 레포는 DB 테스트가 없어 QueryDSL 술어가 뒤집혀 있어도 컴파일과
     * 단위 테스트가 통과한다. 그래서 실제 선정 로직(윈도우 판정 + 마켓·날짜별 latest)을 전부 이 순수
     * 함수로 옮겨 SQL 술어의 정확성과 무관하게 테스트로 보장한다. */
    static List<MarketSnapshotTime> selectRetainedSnapshotTimes(
            List<MarketSnapshotTime> candidates, LocalTime windowStart, LocalTime windowEnd) {
        record GroupKey(Market market, LocalDate date) {}
        return candidates.stream()
                .filter(candidate -> isInWindow(candidate.snapshotTime().toLocalTime(), windowStart, windowEnd))
                .collect(Collectors.groupingBy(candidate -> new GroupKey(
                        candidate.market(), candidate.snapshotTime().toLocalDate())))
                .values()
                .stream()
                .map(group -> group.stream()
                        .max(Comparator.comparing(MarketSnapshotTime::snapshotTime))
                        .orElseThrow())
                .toList();
    }

    private static boolean isInWindow(LocalTime time, LocalTime windowStart, LocalTime windowEnd) {
        return !time.isBefore(windowStart) && time.isBefore(windowEnd);
    }
}
