package dev.eolmae.marketmonitor.domain.marketmap.repository;

import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapScaleThreshold;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMapScaleThresholdRepository extends JpaRepository<MarketMapScaleThreshold, Long> {

    boolean existsByThresholdPercent(BigDecimal thresholdPercent);

    boolean existsByThresholdPercentAndIdNot(BigDecimal thresholdPercent, Long id);
}
