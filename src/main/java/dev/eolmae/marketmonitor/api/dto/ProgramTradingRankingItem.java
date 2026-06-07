package dev.eolmae.marketmonitor.api.dto;

import java.math.BigDecimal;

public record ProgramTradingRankingItem(
        int rank,
        String stockCode,
        String stockName,
        BigDecimal programBuyAmount,
        BigDecimal programSellAmount,
        BigDecimal programNetBuyAmount) {}
