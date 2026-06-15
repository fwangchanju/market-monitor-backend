package dev.eolmae.marketmonitor.common.enums;

import java.time.format.DateTimeFormatter;

public enum DateTimePattern {
    DATE("yyyyMMdd"),
    TIME("HHmmss"),
    DATETIME("yyyyMMddHHmmss");

    private final DateTimeFormatter formatter;

    DateTimePattern(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    public DateTimeFormatter formatter() {
        return formatter;
    }
}
