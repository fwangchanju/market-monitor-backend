package dev.eolmae.marketmonitor.common.util;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DateParser {

    private static final Logger log = LoggerFactory.getLogger(DateParser.class);

    private DateParser() {}

    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DateTimePattern.DATE.formatter());
        } catch (Exception e) {
            log.warn("날짜 파싱 실패: value={}, format={}", value, DateTimePattern.DATE, e);
            return null;
        }
    }

    public static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimePattern.DATETIME.formatter());
        } catch (Exception e) {
            log.warn("날짜 파싱 실패: value={}, format={}", value, DateTimePattern.DATETIME, e);
            return null;
        }
    }

    public static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), DateTimePattern.TIME.formatter());
        } catch (Exception e) {
            log.warn("날짜 파싱 실패: value={}, format={}", value, DateTimePattern.TIME, e);
            return null;
        }
    }
}
