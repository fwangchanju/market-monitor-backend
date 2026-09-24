package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomScaleThreshold;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomScaleThresholdRepository extends JpaRepository<CustomScaleThreshold, Long> {

    boolean existsByThresholdPercent(BigDecimal thresholdPercent);

    boolean existsByThresholdPercentAndIdNot(BigDecimal thresholdPercent, Long id);
}
