package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;
import java.util.List;

public record CategoryChangeRateMarketRanking(
        Market market, List<CategoryChangeRateItem> items, BigDecimal indexChangeRate) {

    /** indexChangeRate 없이 랭킹만 다루는 호출부(스냅샷 서비스 자체는 지수 개념을 모른다)용. */
    public CategoryChangeRateMarketRanking(Market market, List<CategoryChangeRateItem> items) {
        this(market, items, null);
    }

    public CategoryChangeRateMarketRanking withIndexChangeRate(BigDecimal indexChangeRate) {
        return new CategoryChangeRateMarketRanking(market, items, indexChangeRate);
    }
}
