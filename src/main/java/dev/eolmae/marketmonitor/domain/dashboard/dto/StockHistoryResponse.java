package dev.eolmae.marketmonitor.domain.dashboard.dto;

import java.util.List;

public record StockHistoryResponse<T>(String stockCode, List<T> items) {}
