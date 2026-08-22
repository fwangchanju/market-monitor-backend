package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketMapCategoryNode(
        Long categoryId,
        String categoryName,
        boolean isExcluded,
        BigDecimal totalMarketValue,
        List<MarketMapCategoryNode> children,
        List<MarketMapItem> items) {}
