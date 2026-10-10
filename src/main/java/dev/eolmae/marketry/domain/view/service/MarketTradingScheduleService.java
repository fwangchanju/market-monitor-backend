package dev.eolmae.marketry.domain.view.service;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.entity.IntegratedSessions;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingSession;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse.TimeWindow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketTradingScheduleService {
    // 토스에 없는 시간외 단일가 시작은 증권사 거래시간 공지를 참고해 정규장 종료+30분으로 계산한다(수능일 지연 반영).
    private static final int NXT_FILTER_END_OFFSET_MINUTES = 30;

    private final MarketCalendarService marketCalendarService;

    public MarketTradingScheduleResponse getTradingSchedule(LocalDate date) {
        MarketCalendar calendar =
                marketCalendarService.findByCountryAndDate(Country.KR, date).orElse(null);
        if (calendar == null) {
            return MarketTradingScheduleResponse.empty(date, MarketCalendarStatus.FAILED);
        }
        if (calendar.getStatus() != MarketCalendarStatus.TRADING_DAY) {
            return MarketTradingScheduleResponse.empty(date, calendar.getStatus());
        }
        IntegratedSessions sessions = calendar.getIntegrated();
        if (sessions == null) {
            return MarketTradingScheduleResponse.empty(date, MarketCalendarStatus.FAILED);
        }
        TradingSession afterMarket = sessions.afterMarket();
        TimeWindow afterWindow = null;
        if (afterMarket != null) {
            OffsetDateTime startTime = afterMarket.singlePriceAuctionEndTime();
            if (startTime == null) {
                startTime = afterMarket.startTime();
            }
            afterWindow = createWindow(startTime, afterMarket.endTime());
        }
        return new MarketTradingScheduleResponse(
                date,
                calendar.getStatus(),
                createSessionWindow(sessions.preMarket()),
                createSessionWindow(sessions.regularMarket()),
                afterWindow,
                createNxtOnlyWindows(sessions));
    }

    private List<TimeWindow> createNxtOnlyWindows(IntegratedSessions sessions) {
        List<TimeWindow> windows = new ArrayList<>();
        TradingSession preMarket = sessions.preMarket();
        if (preMarket != null) {
            TimeWindow morning = createWindow(preMarket.startTime(), preMarket.singlePriceAuctionStartTime());
            if (morning != null) {
                windows.add(morning);
            }
        }
        TradingSession regularMarket = sessions.regularMarket();
        TradingSession afterMarket = sessions.afterMarket();
        if (regularMarket != null && regularMarket.endTime() != null && afterMarket != null) {
            TimeWindow afternoon = createWindow(
                    afterMarket.singlePriceAuctionEndTime(),
                    regularMarket.endTime().plusMinutes(NXT_FILTER_END_OFFSET_MINUTES));
            if (afternoon != null) {
                windows.add(afternoon);
            }
        }
        return List.copyOf(windows);
    }

    private TimeWindow createSessionWindow(TradingSession session) {
        if (session == null) {
            return null;
        }
        return createWindow(session.startTime(), session.endTime());
    }

    private TimeWindow createWindow(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null || startTime.isBefore(endTime) == false) {
            return null;
        }
        return new TimeWindow(
                startTime.atZoneSameInstant(Zone.KST.zoneId()).toLocalDateTime(),
                endTime.atZoneSameInstant(Zone.KST.zoneId()).toLocalDateTime());
    }
}
