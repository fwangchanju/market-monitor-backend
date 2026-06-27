package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;

public record WatchStockResponse(String stockCode, String stockName, Market market, boolean isMain) {}
