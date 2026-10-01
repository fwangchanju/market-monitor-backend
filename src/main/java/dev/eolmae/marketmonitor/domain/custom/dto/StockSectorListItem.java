package dev.eolmae.marketmonitor.domain.custom.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;

public record StockSectorListItem(
        String stockCode,
        Market market,
        String stockName,
        boolean nxtEnabled,
        String alias,
        BigDecimal totalMarketValue,
        String marketValueTier,
        String industryName,
        String parentSectorName,
        String sectorName,
        Long sectorId) {}
