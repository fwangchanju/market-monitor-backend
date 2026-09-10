package dev.eolmae.marketmonitor.domain.view.dto;

import java.util.List;

public record CategoryChangeRateItem(
        Long categoryId,
        String categoryName,
        int depth,
        List<CategoryTierBreakdown> now,
        List<CategoryTierBreakdown> before) {

    public static CategoryChangeRateItem withoutBefore(Long categoryId, List<CategoryTierBreakdown> now) {
        return new CategoryChangeRateItem(categoryId, null, 0, now, null);
    }

    public static CategoryChangeRateItem withBefore(
            Long categoryId, List<CategoryTierBreakdown> now, List<CategoryTierBreakdown> before) {
        return new CategoryChangeRateItem(categoryId, null, 0, now, before);
    }

    public CategoryChangeRateItem withCategory(String categoryName, int depth) {
        return new CategoryChangeRateItem(categoryId, categoryName, depth, now, before);
    }
}
