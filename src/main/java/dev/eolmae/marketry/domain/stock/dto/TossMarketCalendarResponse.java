package dev.eolmae.marketry.domain.stock.dto;

import dev.eolmae.marketry.domain.stock.entity.IntegratedSessions;
import java.time.LocalDate;

public record TossMarketCalendarResponse(Result result) {
    public record Result(Day today, Day previousBusinessDay, Day nextBusinessDay) {}

    public record Day(LocalDate date, IntegratedSessions integrated) {}
}
