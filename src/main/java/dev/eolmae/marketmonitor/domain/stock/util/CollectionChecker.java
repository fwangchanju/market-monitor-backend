package dev.eolmae.marketmonitor.domain.stock.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 수집 스케줄(평일 08:00~20:00) 기준으로, 특정 시점에 데이터가 있어야 정상인지 판별하는 유틸
public final class CollectionChecker {

    private CollectionChecker() {}

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    public static boolean isWeekend(LocalDateTime dateTime) {
        return isWeekend(dateTime.toLocalDate());
    }

    public static boolean isWeekday(LocalDate date) {
        return !isWeekend(date);
    }

    public static boolean isWeekday(LocalDateTime dateTime) {
        return isWeekday(dateTime.toLocalDate());
    }

    /** from이 주말이면 직전 거래일인(휴무는 고려하지 않고) 금요일로 반환. */
    public static LocalDate previousTradingDay(LocalDate from) {
        return switch (from.getDayOfWeek()) {
            case MONDAY -> from.minusDays(3);
            case SUNDAY -> from.minusDays(2);
            default -> from.minusDays(1);
        };
    }
}
