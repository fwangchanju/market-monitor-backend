package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingSession;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCalendarTimeService {

    private final MarketCalendarService marketCalendarService;
    private final MarketHoursProperties marketHoursProperties;

    @Value("${collect.start-hour}")
    private int startHour;

    @Value("${collect.end-hour}")
    private int endHour;

    public CalendarDayTimes resolve(LocalDate date) {
        return resolveStoredCalendar(date, false);
    }

    public CalendarDayTimes resolveForCollection(LocalDate date) {
        return resolveStoredCalendar(date, true);
    }

    private CalendarDayTimes resolveStoredCalendar(LocalDate date, boolean checkMissingWeekend) {
        MarketCalendar calendar;
        try {
            calendar =
                    marketCalendarService.findByCountryAndDate(Country.KR, date).orElse(null);
        } catch (Exception exception) {
            log.warn(
                    "[거래일시간표] 조회 실패로 기본 시간 사용 | context : {}|{}",
                    date,
                    exception.getClass().getSimpleName());
            return resolve(date, null);
        }
        if (checkMissingWeekend
                && calendar == null
                && (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            return new CalendarDayTimes(true, null, null, null, null, null);
        }
        return resolve(date, calendar);
    }

    public CalendarDayTimes resolve(LocalDate date, MarketCalendar calendar) {
        if (calendar != null && calendar.getStatus() == MarketCalendarStatus.HOLIDAY) {
            return new CalendarDayTimes(true, null, null, null, null, null);
        }
        LocalDateTime defaultClosingStart = date.atTime(marketHoursProperties.closeWindowStart());
        LocalDateTime defaultClosingEnd = date.atTime(marketHoursProperties.afterHoursStart());
        if (calendar == null || calendar.getStatus() == MarketCalendarStatus.FAILED) {
            log.warn(
                    "[거래일시간표] 시간표 없음 또는 실패로 기본 시간 사용 | context : {}|{}",
                    date,
                    calendar == null ? "없음" : calendar.getStatus());
            return new CalendarDayTimes(
                    false,
                    date.atTime(startHour, 0),
                    date.atTime(endHour, 0),
                    defaultClosingStart,
                    defaultClosingStart,
                    defaultClosingEnd);
        }
        var integrated = calendar.getIntegrated();
        var sessions = Stream.of(integrated.preMarket(), integrated.regularMarket(), integrated.afterMarket())
                .filter(Objects::nonNull)
                .toList();
        LocalDateTime collectionStart = sessions.stream()
                .map(TradingSession::startTime)
                .map(this::toDomesticTime)
                .min(LocalDateTime::compareTo)
                .orElseThrow();
        LocalDateTime collectionEnd = sessions.stream()
                .map(TradingSession::endTime)
                .map(this::toDomesticTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow();
        LocalDateTime regularEnd = integrated.regularMarket() == null
                ? defaultClosingStart
                : toDomesticTime(integrated.regularMarket().endTime());
        LocalDateTime closingStart = defaultClosingStart;
        LocalDateTime closingEnd = defaultClosingEnd;
        if (integrated.regularMarket() != null
                && integrated.afterMarket() != null
                && integrated.afterMarket().singlePriceAuctionEndTime() != null) {
            LocalDateTime auctionEnd = toDomesticTime(integrated.afterMarket().singlePriceAuctionEndTime());
            if (regularEnd.isBefore(auctionEnd)) {
                closingStart = regularEnd;
                closingEnd = auctionEnd;
            }
        }
        return new CalendarDayTimes(false, collectionStart, collectionEnd, regularEnd, closingStart, closingEnd);
    }

    private LocalDateTime toDomesticTime(OffsetDateTime time) {
        return time.atZoneSameInstant(Zone.KST.zoneId()).toLocalDateTime();
    }
}
