package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.util.List;

// tierBreakdown: 이 카테고리(하위 카테고리 포함) 최신 스냅샷 기준, 시가총액 구간별 등락률 원시 합계.
// 이미 나눠진 평균이 아니라 분자/분모라, 호출부가 원하는 구간만 골라 합산한 뒤 마지막에 나눠야 한다
// (여러 구간을 조합할 때 이미 나뉜 평균끼리 다시 평균내면 틀림). 아직 스냅샷이 한 번도 안 쌓인
// 카테고리(신설 직후 등)는 빈 리스트.
public record MarketMapCategoryNode(
        Long categoryId,
        String categoryName,
        boolean isExcluded,
        BigDecimal totalMarketValue,
        List<CategoryTierBreakdown> tierBreakdown,
        List<MarketMapCategoryNode> children,
        List<MarketMapItem> items) {

    // 기본 마켓맵 노드용 — exclude 대상 아님, 변화율 미계산, 자식 없음(1뎁스).
    public static MarketMapCategoryNode leaf(
            Long categoryId, String categoryName, BigDecimal totalMarketValue, List<MarketMapItem> items) {
        return new MarketMapCategoryNode(categoryId, categoryName, false, totalMarketValue, List.of(), List.of(), items);
    }
}
