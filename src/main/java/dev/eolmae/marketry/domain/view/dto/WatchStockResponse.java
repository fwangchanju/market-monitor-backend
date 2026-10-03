package dev.eolmae.marketry.domain.view.dto;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.enums.RegisterBy;

public record WatchStockResponse(
        String stockCode, String stockName, Market market, boolean isMain, RegisterBy registerBy) {}
