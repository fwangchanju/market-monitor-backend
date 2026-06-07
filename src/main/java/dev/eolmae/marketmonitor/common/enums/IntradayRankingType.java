package dev.eolmae.marketmonitor.common.enums;

public enum IntradayRankingType {
    NET_BUY("1"), // ka10065 trde_tp
    NET_SELL("2");

    private final String code;

    IntradayRankingType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
