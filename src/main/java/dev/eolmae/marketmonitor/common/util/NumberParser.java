package dev.eolmae.marketmonitor.common.util;

import java.math.BigDecimal;
import org.apache.commons.lang3.StringUtils;

public final class NumberParser {

    private NumberParser() {}

    public static BigDecimal parseBigDecimal(String value) {
        if (StringUtils.isBlank(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static long parseLong(String value) {
        if (StringUtils.isBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static int parseInt(String value) {
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
