package dev.eolmae.marketmonitor.domain.custom.dto;

import java.util.List;

public record SectorDeletePreview(
        String sectorName, boolean deletable, List<StockSectorItem> blockingStocks, List<String> deletableSectors) {

    public static SectorDeletePreview blocked(String sectorName, List<StockSectorItem> blockingStocks) {
        return new SectorDeletePreview(sectorName, false, blockingStocks, List.of());
    }

    public static SectorDeletePreview deletable(String sectorName, List<String> deletableSectors) {
        return new SectorDeletePreview(sectorName, true, List.of(), deletableSectors);
    }
}
