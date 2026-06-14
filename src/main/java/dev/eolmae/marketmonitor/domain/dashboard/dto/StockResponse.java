package dev.eolmae.marketmonitor.domain.dashboard.dto;

import dev.eolmae.marketmonitor.common.enums.Market;

public record StockResponse(String stockCode, String stockName, Market marketType) {}
