package dev.eolmae.marketmonitor.domain.stock.util;

import dev.eolmae.marketmonitor.common.util.NumberParser;
import java.math.BigDecimal;
import org.apache.commons.lang3.StringUtils;

public final class KiwoomValueParser {

    private KiwoomValueParser() {}

    public static BigDecimal parseBigDecimal(String value) {
        String normalized = StringUtils.trimToEmpty(value).replace(",", "");
        if ("-".equals(normalized)) {
            return BigDecimal.ZERO;
        }
        if (normalized.startsWith("--")) {
            normalized = normalized.substring(1);
        }
        return NumberParser.parseBigDecimal(normalized);
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
