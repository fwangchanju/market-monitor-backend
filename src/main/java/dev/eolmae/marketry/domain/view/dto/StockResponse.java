package dev.eolmae.marketry.domain.view.dto;

import dev.eolmae.marketry.common.enums.Market;

public record StockResponse(String stockCode, String stockName, Market market) {}
