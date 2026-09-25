package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomScaleThreshold;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomScaleThresholdRepository extends JpaRepository<CustomScaleThreshold, Long> {

    boolean existsByThresholdPercent(BigDecimal thresholdPercent);

    List<CustomScaleThreshold> findAllByUserId(Long userId);

    boolean existsByUserIdAndThresholdPercent(Long userId, BigDecimal thresholdPercent);

    boolean existsByUserIdAndThresholdPercentAndIdNot(Long userId, BigDecimal thresholdPercent, Long id);

    Optional<CustomScaleThreshold> findByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);

    void deleteAllByUserId(Long userId);

    boolean existsByThresholdPercentAndIdNot(BigDecimal thresholdPercent, Long id);
}
