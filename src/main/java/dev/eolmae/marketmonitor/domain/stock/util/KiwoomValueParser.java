package dev.eolmae.marketmonitor.domain.stock.util;

import dev.eolmae.marketmonitor.common.util.NumberParser;
import java.math.BigDecimal;
import org.apache.commons.lang3.StringUtils;


public final class KiwoomValueParser {

    private KiwoomValueParser() {}

    // 키움 API 응답 숫자 문자열 전처리: 공백/쉼표 제거, 대시 단독(데이터 없음) → 빈 문자열, -- 접두사(음수 오류 패턴) → - 로 정규화
    private static String normalize(String value) {
        String normalized = StringUtils.trimToEmpty(value).replace(",", "");
        if ("-".equals(normalized)) {
            return "";
        }
        if (normalized.startsWith("--")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    public static BigDecimal parseBigDecimal(String value) {
        return NumberParser.parseBigDecimal(normalize(value));
    }

    public static long parseLong(String value) {
        return NumberParser.parseLong(normalize(value));
    }

    public static int parseInt(String value) {
        return NumberParser.parseInt(normalize(value));
    }
}
