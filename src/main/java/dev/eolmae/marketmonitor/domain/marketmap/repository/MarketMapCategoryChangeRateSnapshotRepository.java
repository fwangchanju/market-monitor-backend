package dev.eolmae.marketmonitor.domain.marketmap.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategoryChangeRateSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMapCategoryChangeRateSnapshotRepository
        extends JpaRepository<MarketMapCategoryChangeRateSnapshot, Long> {

    Optional<MarketMapCategoryChangeRateSnapshot> findFirstByMarketTypeOrderBySnapshotTimeDesc(Market market);

    List<MarketMapCategoryChangeRateSnapshot> findByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);
}
