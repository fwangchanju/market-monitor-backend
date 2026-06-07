package dev.eolmae.marketmonitor.api.dto;

import java.util.List;

public record StockHistoryResponse<T>(String stockCode, List<T> items) {}
