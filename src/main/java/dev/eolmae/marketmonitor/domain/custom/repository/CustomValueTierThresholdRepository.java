package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomValueTierThreshold;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomValueTierThresholdRepository extends JpaRepository<CustomValueTierThreshold, Long> {

    List<CustomValueTierThreshold> findAllByOrderByThresholdValueAsc();
}
