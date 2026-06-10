package dev.eolmae.marketmonitor.domain.stock.enums;

public enum InvestorType {
    PERSONAL, // 개인
    FOREIGNER, // 외국인
    INSTITUTION, // 기관계
    FINANCIAL_INVESTMENT, // 금융투자
    TRUST, // 투신
    PENSION_FUND, // 연기금등
    PRIVATE_FUND, // 사모펀드
    INSURANCE, // 보험
    BANK, // 은행
    OTHER_FINANCE, // 기타금융
    GOVERNMENT, // 국가
    OTHER_CORP, // 기타법인
    FOREIGN_COMPANY, // 외국계
    /** API 파라미터 전용 (DB 저장 금지) — FOREIGNER + FOREIGN_COMPANY 합산 조회용 */
    FOREIGN_TOTAL
}
