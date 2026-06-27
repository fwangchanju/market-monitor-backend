package dev.eolmae.marketmonitor.domain.stock.enums;

import dev.eolmae.marketmonitor.domain.view.enums.RankingType;

public enum ProgramRankingType implements NetAmountRanking {
    NET_BUY("2"), // ka90003 trde_upper_tp: 2=순매수
    NET_SELL("1"); // ka90003 trde_upper_tp: 1=순매도

    private final String code;

    ProgramRankingType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    @Override
    public boolean isSell() {
        return this == NET_SELL;
    }

    public static ProgramRankingType from(RankingType type) {
        return type.isSell() ? NET_SELL : NET_BUY;
    }
}
