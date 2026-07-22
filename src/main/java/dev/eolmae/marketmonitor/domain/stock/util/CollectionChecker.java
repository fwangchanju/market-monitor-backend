package dev.eolmae.marketmonitor.domain.stock.util;

import dev.eolmae.marketmonitor.common.enums.Zone;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

// 수집 스케줄(평일 08:00~20:00) 기준으로, 특정 시점에 데이터가 있어야 정상인지 판별하는 유틸
public final class CollectionChecker {

    private static final LocalTime COLLECTION_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime COLLECTION_END_TIME = LocalTime.of(20, 0);

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

    /** from에서 거래일 기준 days일 전 반환(휴무는 고려하지 않음). */
    public static LocalDate previousTradingDay(LocalDate from, int days) {
        for (int i = 0; i < days; i++) {
            from = previousTradingDay(from);
        }
        return from;
    }

    /** 지금이 평일 수집 시간대(08:00~20:00)인지 여부. */
    public static boolean isTradingTime() {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        LocalTime nowTime = now.toLocalTime();
        return isWeekday(now) && !nowTime.isBefore(COLLECTION_START_TIME) && !nowTime.isAfter(COLLECTION_END_TIME);
    }

    /** 수집 시간대면 현재 시각을 시 단위로 절삭, 아니면 직전 수집일의 종료 시각. */
    public static LocalDateTime expectedSnapshotTime() {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        if (isTradingTime()) {
            return now.truncatedTo(ChronoUnit.HOURS);
        }

        LocalDate today = now.toLocalDate();
        boolean todayAlreadyEnded = isWeekday(today) && now.toLocalTime().isAfter(COLLECTION_END_TIME);
        LocalDate referenceDate = todayAlreadyEnded ? today : previousTradingDay(today);
        return referenceDate.atTime(COLLECTION_END_TIME);
    }
}
