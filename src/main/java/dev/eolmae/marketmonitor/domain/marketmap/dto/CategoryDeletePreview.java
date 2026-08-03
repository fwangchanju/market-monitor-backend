package dev.eolmae.marketmonitor.domain.marketmap.dto;

import java.util.List;

public record CategoryDeletePreview(
        String categoryName, boolean deletable, List<BlockingStockItem> blockingStocks, List<String> deletableCategories) {

    public static CategoryDeletePreview blocked(String categoryName, List<BlockingStockItem> blockingStocks) {
        return new CategoryDeletePreview(categoryName, false, blockingStocks, List.of());
    }

    public static CategoryDeletePreview deletable(String categoryName, List<String> deletableCategories) {
        return new CategoryDeletePreview(categoryName, true, List.of(), deletableCategories);
    }
}
