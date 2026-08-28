package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.util.List;

// weightedAvgChangeRate/simpleAvgChangeRate: 이 카테고리(하위 카테고리 포함) 최신 스냅샷 기준 평균
// 등락률 — 아직 스냅샷이 한 번도 안 쌓인 카테고리(신설 직후 등)는 null.
public record MarketMapCategoryNode(
        Long categoryId,
        String categoryName,
        boolean isExcluded,
        BigDecimal totalMarketValue,
        BigDecimal weightedAvgChangeRate,
        BigDecimal simpleAvgChangeRate,
        List<MarketMapCategoryNode> children,
        List<MarketMapItem> items) {

    // 기본 마켓맵 노드용 — exclude 대상 아님, 변화율 미계산, 자식 없음(1뎁스).
    public static MarketMapCategoryNode leaf(
            Long categoryId, String categoryName, BigDecimal totalMarketValue, List<MarketMapItem> items) {
        return new MarketMapCategoryNode(categoryId, categoryName, false, totalMarketValue, null, null, List.of(), items);
    }
}
