package dev.eolmae.marketmonitor.common.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 장중 투자자별 매매 상위(ka10065) 전용 투자자 구분.
 * DB 저장값으로 사용됨 (EnumType.STRING) — FOREIGN_TOTAL 제외.
 * <p>
 * {@link InvestorType}과 별도로 관리 — ka10065 API 코드(trde_ori_tp) 포함.
 */
public enum IntradayInvestorType {
    FOREIGNER("9000"), // 외국인
    FOREIGN_COMPANY("9100"), // 외국계
    INSTITUTION("9999"), // 기관계
    PENSION_FUND("6000"), // 연기금
    TRUST("3000"), // 투신
    /** API 파라미터 전용 (DB 저장 금지) — FOREIGNER + FOREIGN_COMPANY 합산 조회용 */
    FOREIGN_TOTAL(null);

    private final String apiCode; // ka10065 trde_ori_tp (FOREIGN_TOTAL은 null)

    IntradayInvestorType(String apiCode) {
        this.apiCode = apiCode;
    }

    public String apiCode() {
        return apiCode;
    }

    /** DB에 저장 가능한 값만 반환 (FOREIGN_TOTAL 제외) */
    public static List<IntradayInvestorType> storableValues() {
        return Arrays.stream(values()).filter(v -> v != FOREIGN_TOTAL).toList();
    }
}
