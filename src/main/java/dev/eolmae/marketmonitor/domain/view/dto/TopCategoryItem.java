package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;

public record TopCategoryItem(String categoryName, BigDecimal changeRate) {}
