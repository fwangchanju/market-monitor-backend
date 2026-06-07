package dev.eolmae.marketmonitor.common.enums;

public enum ProgramRankingType {
    NET_BUY("2"), // ka90003 trde_upper_tp: 2=순매수
    NET_SELL("1"); // ka90003 trde_upper_tp: 1=순매도

    private final String code;

    ProgramRankingType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
