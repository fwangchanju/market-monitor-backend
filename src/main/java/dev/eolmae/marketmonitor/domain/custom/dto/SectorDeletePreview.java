package dev.eolmae.marketmonitor.domain.custom.dto;

import java.util.List;

public record SectorDeletePreview(
        String categoryName,
        boolean deletable,
        List<StockSectorItem> blockingStocks,
        List<String> deletableCategories) {

    public static SectorDeletePreview blocked(String categoryName, List<StockSectorItem> blockingStocks) {
        return new SectorDeletePreview(categoryName, false, blockingStocks, List.of());
    }

    public static SectorDeletePreview deletable(String categoryName, List<String> deletableCategories) {
        return new SectorDeletePreview(categoryName, true, List.of(), deletableCategories);
    }
}
