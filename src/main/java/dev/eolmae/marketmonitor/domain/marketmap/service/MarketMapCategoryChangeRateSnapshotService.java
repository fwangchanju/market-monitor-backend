package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategoryChangeRateSnapshot;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketValueTierThresholdRepository;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마켓맵 카테고리별(하위 카테고리 재귀 포함) × 시가총액 구간별 등락률 원시 합계(분자/분모) 스냅샷 저장.
 * 트리 자체는 호출부가 MarketMapQueryService로 이미 만들어서 넘겨준다 — 이 서비스가 MarketMapQueryService를
 * 직접 의존하면, MarketMapQueryService가 최신 스냅샷 값을 읽어 응답에 채워 넣을 때(반대 방향 의존) 순환
 * 참조가 된다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketMapCategoryChangeRateSnapshotService {

    private static final int SCALE = 4;

    private final MarketMapCategoryChangeRateSnapshotRepository marketMapCategoryChangeRateSnapshotRepository;
    private final MarketValueTierThresholdRepository marketValueTierThresholdRepository;

    @Transactional
    public void captureSnapshot(Market market, LocalDateTime snapshotTime, List<MarketMapCategoryNode> tree) {
        if (tree.isEmpty()) {
            return;
        }
        Map<String, MarketValueTierThreshold> tierByLabel = marketValueTierThresholdRepository.findAll().stream()
                .collect(Collectors.toMap(MarketValueTierThreshold::getLabel, Function.identity()));
        List<MarketMapCategoryChangeRateSnapshot> snapshots = new ArrayList<>();
        collectSnapshots(tree, market, snapshotTime, tierByLabel, snapshots);
        marketMapCategoryChangeRateSnapshotRepository.saveAll(snapshots);
    }

    /** 라이브 조회용 — 최신 시각을 먼저 찾은 다음 그 시각으로 findRanking을 호출한다. */
    public SnapshotResponse<CategoryChangeRateItem> findLatestRanking(Market market, int beforeMinutes) {
        return marketMapCategoryChangeRateSnapshotRepository
                .findFirstByMarketTypeOrderBySnapshotTimeDesc(market)
                .map(latest -> findRanking(market, latest.getSnapshotTime(), beforeMinutes))
                .orElseGet(SnapshotResponse::empty);
    }

    /**
     * 카테고리별 현재(snapshotTime)/직전(beforeMinutes분 전) 등락률 평균 랭킹 조회. 호출부가 이미 알고 있는
     * 정확한 snapshotTime을 받는다 — 텔레그램 캡션처럼 특정 시각이 이미 정해진 호출부용. beforeMinutes
     * 시각에 정확히 일치하는 스냅샷이 없으면(장 시작 직후, 수집 gap 등) 해당 카테고리는 before 없이
     * 내려준다 — 가장 가까운 다른 시점 데이터로 조용히 대체하지 않는다.
     */
    public SnapshotResponse<CategoryChangeRateItem> findRanking(Market market, LocalDateTime snapshotTime, int beforeMinutes) {
        Map<Long, List<CategoryTierBreakdown>> nowByCategoryId = findTierBreakdownsByCategoryId(market, snapshotTime);
        if (nowByCategoryId.isEmpty()) {
            return SnapshotResponse.empty();
        }

        LocalDateTime beforeTime = snapshotTime.minusMinutes(beforeMinutes);
        Map<Long, List<CategoryTierBreakdown>> beforeByCategoryId = findTierBreakdownsByCategoryId(market, beforeTime);

        List<CategoryChangeRateItem> items = nowByCategoryId.entrySet().stream()
                .map(entry -> toItem(entry.getKey(), entry.getValue(), beforeByCategoryId.get(entry.getKey())))
                .toList();
        return new SnapshotResponse<>(snapshotTime, items);
    }

    /** market의 정확히 snapshotTime 시각, 카테고리별(하위 재귀 포함) 시가총액 구간별 등락률 원시 합계.
     * 필터(어떤 구간을 포함할지)는 호출부(화면)가 알고 있으므로 여기서는 합산/나눗셈 없이 원시값 그대로
     * 내려준다. 그 시각에 스냅샷이 없는 카테고리는 결과 맵에 아예 없음(빈 리스트로 채우지 않음). */
    public Map<Long, List<CategoryTierBreakdown>> findTierBreakdownsByCategoryId(Market market, LocalDateTime snapshotTime) {
        Map<Long, MarketValueTierThreshold> tierById = marketValueTierThresholdRepository.findAll().stream()
                .collect(Collectors.toMap(MarketValueTierThreshold::getId, Function.identity()));
        return marketMapCategoryChangeRateSnapshotRepository.findByMarketTypeAndSnapshotTime(market, snapshotTime).stream()
                .collect(Collectors.groupingBy(
                        MarketMapCategoryChangeRateSnapshot::getCategoryId,
                        Collectors.mapping(row -> toBreakdown(row, tierById), Collectors.toList())));
    }

    private CategoryTierBreakdown toBreakdown(
            MarketMapCategoryChangeRateSnapshot row, Map<Long, MarketValueTierThreshold> tierById) {
        MarketValueTierThreshold tier = tierById.get(row.getMarketValueTierId());
        return new CategoryTierBreakdown(
                tier.getId(), tier.getLabel(), row.getWeightedSum(), row.getTotalValue(), row.getSimpleSum(), row.getItemCount());
    }

    private CategoryChangeRateItem toItem(Long categoryId, List<CategoryTierBreakdown> now, List<CategoryTierBreakdown> before) {
        if (before == null) {
            return CategoryChangeRateItem.withoutBefore(categoryId, now);
        }
        return CategoryChangeRateItem.withBefore(categoryId, now, before);
    }

    /** 전달받은 구간별 원시값을 전부 합산한 뒤 마지막에 한 번만 나눈 최종 가중/산술평균 — 화면 필터와
     * 무관하게 항상 전체 구간 기준이 필요한 호출부(텔레그램 캡션 등)용. 이미 나뉜 평균끼리 다시 평균내면
     * 구간별 종목 수/시총 비중을 알 수 없어 틀리기 때문에, 반드시 원시값 합산 후 나눗셈 순서를 지킨다. */
    public SnapshotAverages combine(List<CategoryTierBreakdown> breakdowns) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal simpleSum = BigDecimal.ZERO;
        int itemCount = 0;
        for (CategoryTierBreakdown breakdown : breakdowns) {
            weightedSum = weightedSum.add(breakdown.weightedSum());
            totalValue = totalValue.add(breakdown.totalValue());
            simpleSum = simpleSum.add(breakdown.simpleSum());
            itemCount += breakdown.itemCount();
        }
        BigDecimal weightedAvg = BigDecimal.ZERO;
        if (totalValue.signum() != 0) {
            weightedAvg = weightedSum.divide(totalValue, SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal simpleAvg = BigDecimal.ZERO;
        if (itemCount != 0) {
            simpleAvg = simpleSum.divide(BigDecimal.valueOf(itemCount), SCALE, RoundingMode.HALF_UP);
        }
        return new SnapshotAverages(weightedAvg, simpleAvg);
    }

    private void collectSnapshots(
            List<MarketMapCategoryNode> nodes,
            Market market,
            LocalDateTime snapshotTime,
            Map<String, MarketValueTierThreshold> tierByLabel,
            List<MarketMapCategoryChangeRateSnapshot> out) {
        for (MarketMapCategoryNode node : nodes) {
            Map<String, List<MarketMapItem>> itemsByTierLabel =
                    collectItems(node).stream().collect(Collectors.groupingBy(MarketMapItem::marketValueTier));
            for (Map.Entry<String, List<MarketMapItem>> entry : itemsByTierLabel.entrySet()) {
                Long tierId = tierByLabel.get(entry.getKey()).getId();
                RawSums sums = computeRawSums(entry.getValue());
                out.add(MarketMapCategoryChangeRateSnapshot.create(
                        market,
                        node.categoryId(),
                        tierId,
                        snapshotTime,
                        sums.weightedSum(),
                        sums.totalValue(),
                        sums.simpleSum(),
                        sums.itemCount()));
            }
            collectSnapshots(node.children(), market, snapshotTime, tierByLabel, out);
        }
    }

    private List<MarketMapItem> collectItems(MarketMapCategoryNode node) {
        List<MarketMapItem> items = new ArrayList<>(node.items());
        for (MarketMapCategoryNode child : node.children()) {
            items.addAll(collectItems(child));
        }
        return items;
    }

    // 가중평균의 분자(Σ등락률×시총)/분모(Σ시총), 산술평균의 분자(Σ등락률)/분모(종목 수)를 나누지 않고
    // 원시값 그대로 한 번의 순회로 구한다 — 여러 구간을 조합할 땐 이미 나뉜 평균끼리 다시 평균내면 틀리므로,
    // 나눗셈은 조회 시점에 필요한 구간들을 합산한 뒤 마지막에 한 번만 한다.
    private RawSums computeRawSums(List<MarketMapItem> items) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal simpleSum = BigDecimal.ZERO;
        for (MarketMapItem item : items) {
            totalValue = totalValue.add(item.totalMarketValue());
            weightedSum = weightedSum.add(item.changeRate().multiply(item.totalMarketValue()));
            simpleSum = simpleSum.add(item.changeRate());
        }
        return new RawSums(weightedSum, totalValue, simpleSum, items.size());
    }

    private record RawSums(BigDecimal weightedSum, BigDecimal totalValue, BigDecimal simpleSum, int itemCount) {}
}
