package dev.eolmae.marketmonitor.domain.marketmap.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;

public record StockCategoryListItem(
        String stockCode,
        Market market,
        String stockName,
        String alias,
        BigDecimal totalMarketValue,
        String originCategoryName,
        String parentCategoryName,
        String categoryName,
        Long categoryId) {}
