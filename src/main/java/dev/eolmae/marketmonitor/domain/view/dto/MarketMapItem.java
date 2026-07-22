package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketMapItem(
        String stockCode,
        String stockName,
        BigDecimal lastPrice,
        BigDecimal totalMarketValue,
        BigDecimal changeRate,
        LocalDateTime snapshotTime) {}
