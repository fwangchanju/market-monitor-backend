package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketMapCategoryNode(
        Long categoryId,
        String categoryName,
        boolean isExcluded,
        BigDecimal totalMarketValue,
        List<MarketMapCategoryNode> children,
        List<MarketMapItem> items) {

    // 기본 마켓맵 노드용 — exclude 대상 아님, 변화율 미계산, 자식 없음(1뎁스).
    public static MarketMapCategoryNode leaf(
            Long categoryId, String categoryName, BigDecimal totalMarketValue, List<MarketMapItem> items) {
        return new MarketMapCategoryNode(categoryId, categoryName, false, totalMarketValue, List.of(), items);
    }
}
