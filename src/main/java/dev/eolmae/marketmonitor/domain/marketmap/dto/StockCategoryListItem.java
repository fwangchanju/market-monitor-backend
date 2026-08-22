package dev.eolmae.marketmonitor.domain.marketmap.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.MarketValueTier;
import java.math.BigDecimal;

public record StockCategoryListItem(
        String stockCode,
        Market market,
        String stockName,
        String alias,
        BigDecimal totalMarketValue,
        MarketValueTier marketValueTier,
        String originCategoryName,
        String parentCategoryName,
        String categoryName,
        Long categoryId) {}
