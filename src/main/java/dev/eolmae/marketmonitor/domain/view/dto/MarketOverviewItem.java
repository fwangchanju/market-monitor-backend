package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketOverviewItem(
        Market market,
        String marketStatus,
        BigDecimal indexValue,
        BigDecimal changeValue,
        BigDecimal changeRate,
        BigDecimal tradingValue,
        int upperLimitCount,
        int lowerLimitCount,
        int advancers,
        int decliners,
        int unchangedCount,
        LocalDateTime snapshotTime) {}
