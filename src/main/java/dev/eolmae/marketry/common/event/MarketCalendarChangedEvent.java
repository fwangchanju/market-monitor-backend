package dev.eolmae.marketry.common.event;

import dev.eolmae.marketry.common.enums.Country;
import java.time.LocalDate;

public record MarketCalendarChangedEvent(Country country, LocalDate date) {}
