package dev.eolmae.marketmonitor.domain.stock.enums;

import dev.eolmae.marketmonitor.domain.view.enums.RankingType;

public enum IntradayRankingType implements NetAmountRanking {
    NET_BUY("1"), // ka10065 trde_tp
    NET_SELL("2");

    private final String code;

    IntradayRankingType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    @Override
    public boolean isSell() {
        return this == NET_SELL;
    }

    public static IntradayRankingType from(RankingType type) {
        return type.isSell() ? NET_SELL : NET_BUY;
    }
}
