package dev.eolmae.marketmonitor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProgramTradingHistoryItem(
        LocalDateTime snapshotTime,
        BigDecimal programBuyAmount,
        BigDecimal programSellAmount,
        BigDecimal programNetBuyAmount) {}
