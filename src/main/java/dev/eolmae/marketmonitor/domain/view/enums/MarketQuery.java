package dev.eolmae.marketmonitor.domain.view.enums;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.util.List;

public enum MarketQuery {
    KOSPI,
    KOSDAQ,
    ALL_STOCKS;

    public List<Market> toMarkets() {
        return switch (this) {
            case ALL_STOCKS -> List.of(Market.KOSPI, Market.KOSDAQ);
            default -> List.of(Market.valueOf(name()));
        };
    }
}
