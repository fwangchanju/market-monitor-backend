package dev.eolmae.marketmonitor.domain.view.dto;

import java.util.List;

public record MarketMapCategoryGroup(String categoryName, List<MarketMapItem> items) {}
