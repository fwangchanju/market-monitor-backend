package dev.eolmae.marketmonitor.common.util;

import java.math.BigDecimal;

public class NumberParser {

    private NumberParser() {}

    public static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) return BigDecimal.ZERO;
        String raw = value.replace(",", "").trim();
        // 일부 API는 "--180311" 형태로 음수를 표현 (부호 prefix + 숫자)
        if (raw.startsWith("--")) raw = raw.substring(1);
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static long parseLong(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) return 0L;
        try {
            return Long.parseLong(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static int parseInt(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim()))
            return 0; // TODO StringUtils.isBlank 만 조건으로 사용하고 "-".equals 조건은 그냥 빼버려도 저 아래 NumberFormatException 에서 걸러져서
        // 처리 될거같은데
        try {
            return Integer.parseInt(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
