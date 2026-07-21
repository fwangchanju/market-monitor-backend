package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.enums.RegisterBy;

public record WatchStockResponse(String stockCode, String stockName, Market market, boolean isMain, RegisterBy registerBy) {}
