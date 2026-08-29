package dev.eolmae.marketmonitor.domain.marketmap.repository;

import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketValueTierThresholdRepository extends JpaRepository<MarketValueTierThreshold, Long> {

    List<MarketValueTierThreshold> findAllByOrderByThresholdValueAsc();
}
