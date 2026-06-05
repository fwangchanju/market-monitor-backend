package dev.eolmae.marketmonitor.common.enums;

public enum StockMarketCode {
    KOSPI("0"),        // 유가증권시장(코스피) 주권
    ELW("3"),          // ELW
    MUTUAL_FUND("4"),  // 뮤추얼펀드
    NEW_SHARES("5"),   // 신주인수권
    REITS("6"),        // 리츠(REITs)
    ETF("8"),          // ETF
    HIGH_YIELD("9"),   // 하이일드펀드
    KOSDAQ("10"),      // 코스닥 주권
    K_OTC("30"),       // K-OTC
    KONEX("50");       // 코넥스(KONEX)

    private final String code;

    StockMarketCode(String code) { this.code = code; }

    public String code() { return code; }

    public boolean matches(String marketCode) { return code.equals(marketCode); }
}
