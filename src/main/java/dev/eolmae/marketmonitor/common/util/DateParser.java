package dev.eolmae.marketmonitor.common.util;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
public final class DateParser {

    private DateParser() {}

    public static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value != null ? value.trim() : null, DateTimePattern.DATE.formatter());
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value != null ? value.trim() : null, DateTimePattern.DATETIME.formatter());
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value != null ? value.trim() : null, DateTimePattern.TIME.formatter());
        } catch (Exception e) {
            return null;
        }
    }
}
