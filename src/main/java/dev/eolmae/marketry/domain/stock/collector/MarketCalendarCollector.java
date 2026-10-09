package dev.eolmae.marketry.domain.stock.collector;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.BusinessException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.client.TossMarketCalendarClient;
import dev.eolmae.marketry.domain.stock.dto.TossMarketCalendarResponse;
import dev.eolmae.marketry.domain.stock.dto.TossMarketCalendarResponse.Day;
import dev.eolmae.marketry.domain.stock.entity.IntegratedPeriod;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingPeriod;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCalendarCollector {
    private final TossMarketCalendarClient client;
    private final MarketCalendarService service;

    public boolean collect(LocalDate date) {
        try {
            service.saveSuccess(Country.KR, validateResponse(date, client.fetch(date)));
            log.info("[시장 시간표 저장 완료] | context : KR|{}", date);
            return true;
        } catch (Exception e) {
            String failure = e instanceof BusinessException businessException
                    ? businessException.createLogMessage()
                    : e.getClass().getSimpleName();
            log.warn("[시장 시간표 조회 또는 저장 실패, 기본 시간 사용] | context : KR|{}|{}", date, failure);
            saveFailureSafely(date);
            return false;
        }
    }

    private void saveFailureSafely(LocalDate date) {
        try {
            service.saveFailure(Country.KR, date);
        } catch (Exception e) {
            log.warn(
                    "[시장 시간표 실패 상태 저장 실패, 기본 시간 사용] | context : KR|{}|{}",
                    date,
                    e.getClass().getSimpleName());
        }
    }

    List<MarketCalendar> validateResponse(LocalDate date, TossMarketCalendarResponse response) {
        if (response == null || response.result() == null) {
            throw invalidResponse();
        }
        var result = response.result();
        if (result.today() == null && (result.previousBusinessDay() == null || result.nextBusinessDay() == null)) {
            throw invalidResponse();
        }
        List<MarketCalendar> calendars = new ArrayList<>();
        Set<LocalDate> dates = new HashSet<>();
        if (result.today() != null) {
            validateDay(result.today(), dates, calendars);
            if (date.equals(result.today().date()) == false) {
                throw invalidResponse();
            }
        } else {
            calendars.add(MarketCalendar.create(Country.KR, date, MarketCalendarStatus.HOLIDAY, null));
            dates.add(date);
        }
        if (result.previousBusinessDay() != null) {
            validateDay(result.previousBusinessDay(), dates, calendars);
            if (result.previousBusinessDay().date().isBefore(date) == false) {
                throw invalidResponse();
            }
        }
        if (result.nextBusinessDay() != null) {
            validateDay(result.nextBusinessDay(), dates, calendars);
            if (result.nextBusinessDay().date().isAfter(date) == false) {
                throw invalidResponse();
            }
        }
        return calendars;
    }

    private void validateDay(Day day, Set<LocalDate> dates, List<MarketCalendar> calendars) {
        if (day.date() == null || dates.add(day.date()) == false) {
            throw invalidResponse();
        }
        IntegratedPeriod integrated = day.integrated();
        if (integrated != null) {
            if (integrated.preMarket() == null
                    && integrated.regularMarket() == null
                    && integrated.afterMarket() == null) {
                throw invalidResponse();
            }
            validatePeriod(day.date(), integrated.preMarket());
            validatePeriod(day.date(), integrated.regularMarket());
            validatePeriod(day.date(), integrated.afterMarket());
        }
        calendars.add(MarketCalendar.create(
                Country.KR,
                day.date(),
                integrated == null ? MarketCalendarStatus.HOLIDAY : MarketCalendarStatus.TRADING_DAY,
                integrated));
    }

    private void validatePeriod(LocalDate date, TradingPeriod period) {
        if (period == null) {
            return;
        }
        if (period.startTime() == null
                || period.endTime() == null
                || period.startTime().isBefore(period.endTime()) == false) {
            throw invalidResponse();
        }
        validateTime(date, period.startTime(), period);
        validateTime(date, period.endTime(), period);
        validateTime(date, period.singlePriceAuctionStartTime(), period);
        validateTime(date, period.singlePriceAuctionEndTime(), period);
        if (period.singlePriceAuctionStartTime() != null
                && period.singlePriceAuctionEndTime() != null
                && period.singlePriceAuctionStartTime().isAfter(period.singlePriceAuctionEndTime())) {
            throw invalidResponse();
        }
    }

    private void validateTime(LocalDate date, OffsetDateTime time, TradingPeriod period) {
        if (time == null) {
            return;
        }
        if (time.atZoneSameInstant(Zone.KST.zoneId()).toLocalDate().equals(date) == false
                || time.isBefore(period.startTime())
                || time.isAfter(period.endTime())) {
            throw invalidResponse();
        }
    }

    private BadRequestException invalidResponse() {
        return new BadRequestException(ErrorCode.MARKET_CALENDAR_RESPONSE_INVALID);
    }
}
