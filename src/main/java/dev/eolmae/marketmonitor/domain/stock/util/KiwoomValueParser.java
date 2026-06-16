package dev.eolmae.marketmonitor.domain.stock.util;

import dev.eolmae.marketmonitor.common.util.NumberParser;
import java.math.BigDecimal;
import org.apache.commons.lang3.StringUtils;


public final class KiwoomValueParser {

    private KiwoomValueParser() {}

    // 키움 API 음수 오류 패턴: --1234.56 형태로 오는 경우 -1234.56으로 정규화
    private static String fixDoubleNegative(String value) {
        if (value.startsWith("--")) {
            return value.substring(1);
        }
        return value;
    }

    public static BigDecimal parseBigDecimal(String value) {
        String normalized = StringUtils.trimToEmpty(value).replace(",", "");
        if ("-".equals(normalized)) {
            return BigDecimal.ZERO;
        }
        return NumberParser.parseBigDecimal(fixDoubleNegative(normalized));
    }

    public static long parseLong(String value) {
        String normalized = StringUtils.trimToEmpty(value).replace(",", "");
        if ("-".equals(normalized)) {
            return 0L;
        }
        return NumberParser.parseLong(normalized);
    }

    public static int parseInt(String value) {
        String normalized = StringUtils.trimToEmpty(value).replace(",", "");
        if ("-".equals(normalized)) {
            return 0;
        }
        return NumberParser.parseInt(normalized);
    }
}
