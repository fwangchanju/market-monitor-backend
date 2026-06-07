package dev.eolmae.marketmonitor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProgramTradingDailyHistoryItem(
        LocalDate tradeDate,
        BigDecimal programBuyAmount,
        BigDecimal programSellAmount,
        BigDecimal programNetBuyAmount) {}
