package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCalendarRepository extends JpaRepository<MarketCalendar, Long> {
    Optional<MarketCalendar> findByCountryAndDate(Country country, LocalDate date);

    List<MarketCalendar> findByCountryAndDateIn(Country country, Collection<LocalDate> dates);
}
