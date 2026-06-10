package dev.eolmae.marketmonitor.domain.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProgramTradingDailyHistoryItem(
        LocalDate tradeDate,
        BigDecimal programBuyAmount,
        BigDecimal programSellAmount,
        BigDecimal programNetBuyAmount) {}
