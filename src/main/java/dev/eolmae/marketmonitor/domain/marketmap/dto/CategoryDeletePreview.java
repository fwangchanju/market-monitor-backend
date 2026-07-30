package dev.eolmae.marketmonitor.domain.marketmap.dto;

import java.util.List;

public record CategoryDeletePreview(boolean deletable, List<BlockingStockItem> blockingStocks, List<String> deletableCategories) {

    public static CategoryDeletePreview blocked(List<BlockingStockItem> blockingStocks) {
        return new CategoryDeletePreview(false, blockingStocks, List.of());
    }

    public static CategoryDeletePreview deletable(List<String> deletableCategories) {
        return new CategoryDeletePreview(true, List.of(), deletableCategories);
    }
}
