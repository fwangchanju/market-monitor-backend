package dev.eolmae.marketmonitor.domain.view.enums;

import dev.eolmae.marketmonitor.domain.stock.enums.TradeType;

public enum RankingType implements TradeType {
    NET_BUY,
    NET_SELL;

    @Override
    public boolean isSell() {
        return this == NET_SELL;
    }
}
