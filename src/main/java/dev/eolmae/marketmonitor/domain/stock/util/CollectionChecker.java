package dev.eolmae.marketmonitor.domain.stock.util;

import dev.eolmae.marketmonitor.common.enums.Zone;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

// 수집 스케줄(평일 08:00~20:00) 기준으로, 특정 시점에 데이터가 있어야 정상인지 판별하는 유틸
public final class CollectionChecker {

    private static final LocalTime MARKET_CLOSE = LocalTime.of(20, 0);

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

    public static LocalDateTime close(LocalDate date) {
        return LocalDateTime.of(date, MARKET_CLOSE);
    }

    /** date가 오늘보다 과거면 무조건 마감(20시) 시각, 오늘이면 현재 정각과 마감 중 이른 쪽을 반환. */
    public static LocalDateTime latestSnapshotHour(LocalDate date) {
        LocalDateTime close = close(date);
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        if (date.isBefore(now.toLocalDate())) {
            return close;
        }
        LocalDateTime currentHour = now.truncatedTo(ChronoUnit.HOURS);
        return currentHour.isBefore(close) ? currentHour : close;
    }
}
