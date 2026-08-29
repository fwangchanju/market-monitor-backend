package dev.eolmae.marketmonitor.domain.view.dto;

import java.util.List;

public record CategoryChangeRateItem(Long categoryId, List<CategoryTierBreakdown> now, List<CategoryTierBreakdown> before) {

    public static CategoryChangeRateItem withoutBefore(Long categoryId, List<CategoryTierBreakdown> now) {
        return new CategoryChangeRateItem(categoryId, now, null);
    }

    public static CategoryChangeRateItem withBefore(
            Long categoryId, List<CategoryTierBreakdown> now, List<CategoryTierBreakdown> before) {
        return new CategoryChangeRateItem(categoryId, now, before);
    }
}
