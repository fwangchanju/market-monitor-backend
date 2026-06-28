package dev.eolmae.marketmonitor.common.util;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.apache.commons.lang3.StringUtils;

public final class DateParser {

    private DateParser() {}

    public static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(StringUtils.trim(value), DateTimePattern.DATE.formatter());
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(StringUtils.trim(value), DateTimePattern.DATETIME.formatter());
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(StringUtils.trim(value), DateTimePattern.TIME.formatter());
        } catch (Exception e) {
            return null;
        }
    }
}
