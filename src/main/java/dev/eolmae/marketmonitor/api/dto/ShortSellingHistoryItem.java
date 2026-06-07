package dev.eolmae.marketmonitor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ShortSellingHistoryItem(
        LocalDate tradeDate,
        LocalDateTime snapshotTime, // 오늘 데이터는 수집 시각, 과거 데이터는 null
        BigDecimal closePrice,
        BigDecimal priceChange,
        BigDecimal changeRate,
        long tradingVolume,
        long shortVolume,
        long cumulativeShortVolume,
        BigDecimal shortRatio,
        BigDecimal shortAmount,
        BigDecimal shortAvgPrice) {}
