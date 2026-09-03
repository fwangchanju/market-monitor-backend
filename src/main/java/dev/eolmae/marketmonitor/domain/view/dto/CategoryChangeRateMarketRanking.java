package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.util.List;

public record CategoryChangeRateMarketRanking(Market market, List<CategoryChangeRateItem> items) {}
