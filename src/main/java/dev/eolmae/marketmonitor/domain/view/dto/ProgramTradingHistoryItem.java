package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProgramTradingHistoryItem(
        LocalDateTime snapshotTime,
        BigDecimal programBuyAmount,
        BigDecimal programSellAmount,
        BigDecimal programNetBuyAmount) {}
