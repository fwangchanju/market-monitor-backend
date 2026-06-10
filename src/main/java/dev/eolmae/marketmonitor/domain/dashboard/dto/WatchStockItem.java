package dev.eolmae.marketmonitor.domain.dashboard.dto;

import dev.eolmae.marketmonitor.common.enums.Exchange;

public record WatchStockItem(String stockCode, String stockName, Exchange marketType, boolean isPrimary) {}
