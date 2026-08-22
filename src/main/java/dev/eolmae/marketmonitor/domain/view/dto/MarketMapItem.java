package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.MarketValueTier;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketMapItem(
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        BigDecimal lastPrice,
        BigDecimal totalMarketValue,
        MarketValueTier marketValueTier,
        BigDecimal changeRate,
        LocalDateTime snapshotTime) {}
