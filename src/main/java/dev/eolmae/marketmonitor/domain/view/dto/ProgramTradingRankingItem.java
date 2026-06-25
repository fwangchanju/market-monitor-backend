package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;

public record ProgramTradingRankingItem(
        int rank,
        String stockCode,
        String stockName,
        BigDecimal programBuyAmount,
        BigDecimal programSellAmount,
        BigDecimal programNetBuyAmount) {}
