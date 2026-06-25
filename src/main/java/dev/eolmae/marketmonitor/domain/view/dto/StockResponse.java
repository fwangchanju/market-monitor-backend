package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;

public record StockResponse(String stockCode, String stockName, Market marketType) {}
