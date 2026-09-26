package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.util.List;

public record SectorChangeRateMarketRanking(
        Market market, List<SectorChangeRateItem> items, MarketIndexChangeRate index) {

    /** index 없이 랭킹만 다루는 호출부(스냅샷 서비스 자체는 지수 개념을 모른다)용. */
    public SectorChangeRateMarketRanking(Market market, List<SectorChangeRateItem> items) {
        this(market, items, null);
    }
}
