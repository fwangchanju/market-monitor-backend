package dev.eolmae.marketry.domain.view.enums;

import dev.eolmae.marketry.common.enums.Market;
import java.util.List;

public enum MarketQuery {
    KOSPI,
    KOSDAQ,
    ALL_STOCK;

    public List<Market> toMarkets() {
        return switch (this) {
            case ALL_STOCK -> List.of(Market.KOSPI, Market.KOSDAQ);
            default -> List.of(Market.valueOf(name()));
        };
    }
}
