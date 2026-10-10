package dev.eolmae.marketry.domain.view.dto;

import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MarketTradingScheduleResponse(
        LocalDate date,
        MarketCalendarStatus status,
        TimeWindow preMarket,
        TimeWindow regularMarket,
        TimeWindow afterMarket,
        List<TimeWindow> nxtOnlyWindows) {

    public static MarketTradingScheduleResponse empty(LocalDate date, MarketCalendarStatus status) {
        return new MarketTradingScheduleResponse(date, status, null, null, null, List.of());
    }

    public record TimeWindow(LocalDateTime startTime, LocalDateTime endTime) {}
}
