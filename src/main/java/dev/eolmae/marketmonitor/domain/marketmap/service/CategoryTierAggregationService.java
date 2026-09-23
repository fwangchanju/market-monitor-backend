package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketValueTierThresholdRepository;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마켓맵 카테고리별(하위 카테고리 재귀 포함) × 시가총액 구간별 등락률 원시 합계(분자/분모) 계산.
 * 트리 자체는 호출부가 MarketMapQueryService로 이미 만들어서 넘겨준다 — 이 서비스가 MarketMapQueryService를
 * 직접 의존하면, MarketMapQueryService가 이 결과를 받아 응답에 채워 넣을 때(반대 방향 의존) 순환 참조가 된다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryTierAggregationService {

    private static final int SCALE = 4;

    private final MarketValueTierThresholdRepository marketValueTierThresholdRepository;

    /** 트리(하위 카테고리 재귀 포함)를 카테고리 id별 시가총액 구간별 등락률 원시 합계로 묶는다 —
     * MarketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId가 마켓 하나에 대해
     * 돌려주는 것과 같은 모양이다. items가 하나도 없는 카테고리(자신과 하위 전부 빈 경우)는 결과 맵에
     * 아예 없다. */
    public Map<Long, List<CategoryTierBreakdown>> aggregateByCategory(List<MarketMapCategoryNode> tree) {
        Map<String, MarketValueTierThreshold> tierByLabel = marketValueTierThresholdRepository.findAll().stream()
                .collect(Collectors.toMap(MarketValueTierThreshold::getLabel, Function.identity()));
        Map<Long, List<CategoryTierBreakdown>> breakdownsByCategoryId = new HashMap<>();
        collectBreakdowns(tree, tierByLabel, breakdownsByCategoryId);
        return breakdownsByCategoryId;
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

    private void collectBreakdowns(
            List<MarketMapCategoryNode> nodes,
            Map<String, MarketValueTierThreshold> tierByLabel,
            Map<Long, List<CategoryTierBreakdown>> out) {
        for (MarketMapCategoryNode node : nodes) {
            Map<String, List<MarketMapItem>> itemsByTierLabel =
                    collectItems(node).stream().collect(Collectors.groupingBy(MarketMapItem::marketValueTier));
            List<CategoryTierBreakdown> breakdowns = itemsByTierLabel.entrySet().stream()
                    .map(entry -> toBreakdown(tierByLabel.get(entry.getKey()), entry.getValue()))
                    .toList();
            if (!breakdowns.isEmpty()) {
                out.put(node.categoryId(), breakdowns);
            }
            collectBreakdowns(node.children(), tierByLabel, out);
        }
    }

    private CategoryTierBreakdown toBreakdown(MarketValueTierThreshold tier, List<MarketMapItem> items) {
        RawSums sums = computeRawSums(items);
        return new CategoryTierBreakdown(
                tier.getId(),
                tier.getLabel(),
                sums.weightedSum(),
                sums.totalValue(),
                sums.simpleSum(),
                sums.itemCount());
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
