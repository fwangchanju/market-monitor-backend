package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProgramTradingRankingItem(
        int rank,
        String stockCode,
        String stockName,
        BigDecimal programBuyAmount,
        BigDecimal programSellAmount,
        BigDecimal programNetBuyAmount,
        LocalDateTime snapshotTime) {}
