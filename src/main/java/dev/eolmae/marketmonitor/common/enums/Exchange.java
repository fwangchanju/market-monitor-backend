package dev.eolmae.marketmonitor.common.enums;

public enum Exchange {
    KOSPI,
    KOSDAQ,
    /** API 파라미터 전용 (DB 저장 금지) — KOSPI + KOSDAQ 통합 조회용 */
    ALL
}
