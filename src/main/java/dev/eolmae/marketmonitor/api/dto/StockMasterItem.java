package dev.eolmae.marketmonitor.api.dto;

import dev.eolmae.marketmonitor.common.enums.Exchange;

public record StockMasterItem(String stockCode, String stockName, Exchange marketType) {}
