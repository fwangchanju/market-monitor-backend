package dev.eolmae.marketmonitor.domain.view.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StockHistoryResponse<T>(String stockCode, LocalDateTime snapshotTime, List<T> items) {
    public static <T> StockHistoryResponse<T> empty() {
        return new StockHistoryResponse<>(null, null, List.of());
    }
}
