package dev.eolmae.marketmonitor.domain.view.dto;

import java.util.List;

public record StockHistoryResponse<T>(String stockCode, List<T> items) {
    public static <T> StockHistoryResponse<T> empty() {
        return new StockHistoryResponse<>(null, List.of());
    }
}
