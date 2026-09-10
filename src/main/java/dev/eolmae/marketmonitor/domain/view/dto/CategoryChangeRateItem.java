package dev.eolmae.marketmonitor.domain.view.dto;

import java.util.List;

public record CategoryChangeRateItem(
        Long categoryId,
        String categoryName,
        int depth,
        List<CategoryTierBreakdown> now,
        List<CategoryTierBreakdown> before) {

    // depth 0은 실제 대분류 값이라 "아직 카테고리를 안 붙였다"는 뜻으로 쓸 수 없다 — 대분류 판정
    // (item.depth() == 0)이 장식 안 된 항목까지 대분류로 잘못 고르게 된다. -1은 어떤 depth 필터에도
    // 걸리지 않는다.
    public static CategoryChangeRateItem withoutBefore(Long categoryId, List<CategoryTierBreakdown> now) {
        return new CategoryChangeRateItem(categoryId, null, -1, now, null);
    }

    public static CategoryChangeRateItem withBefore(
            Long categoryId, List<CategoryTierBreakdown> now, List<CategoryTierBreakdown> before) {
        return new CategoryChangeRateItem(categoryId, null, -1, now, before);
    }

    public CategoryChangeRateItem withCategory(String categoryName, int depth) {
        return new CategoryChangeRateItem(categoryId, categoryName, depth, now, before);
    }
}
