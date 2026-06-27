package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ShortSellingHistoryItem(
        LocalDate tradeDate,
        BigDecimal closePrice,
        BigDecimal priceChange,
        BigDecimal changeRate,
        long tradingVolume,
        long shortVolume,
        long cumulativeShortVolume,
        BigDecimal shortRatio,
        BigDecimal shortAmount,
        BigDecimal shortAvgPrice) {}
