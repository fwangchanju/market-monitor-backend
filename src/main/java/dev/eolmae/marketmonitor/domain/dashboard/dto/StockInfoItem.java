package dev.eolmae.marketmonitor.domain.dashboard.dto;

import dev.eolmae.marketmonitor.common.enums.Exchange;

public record StockInfoItem(String stockCode, String stockName, Exchange marketType) {}
