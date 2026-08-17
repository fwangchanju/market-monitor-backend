package dev.eolmae.marketmonitor.domain.marketmap.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;

public record StockCategoryListItem(
        String stockCode,
        String stockName,
        Market market,
        BigDecimal totalMarketValue,
        Long categoryId,
        String parentCategoryName,
        String categoryName) {}
