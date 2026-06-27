package dev.eolmae.marketmonitor.domain.view.enums;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.util.List;

public enum MarketQuery {
    KOSPI,
    KOSDAQ,
    COMBINED;

    public List<Market> toMarkets() {
        return switch (this) {
            case COMBINED -> List.of(Market.KOSPI, Market.KOSDAQ);
            default -> List.of(Market.valueOf(name()));
        };
    }
}
