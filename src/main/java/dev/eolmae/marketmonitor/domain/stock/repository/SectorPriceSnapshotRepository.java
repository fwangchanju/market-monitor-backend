package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SectorPriceSnapshotRepository extends JpaRepository<SectorPriceSnapshot, Long> {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);

    @Query("SELECT MAX(s.snapshotTime) FROM SectorPriceSnapshot s WHERE s.marketType = :market")
    Optional<LocalDateTime> findMaxSnapshotTimeByMarketType(Market market);

    List<SectorPriceSnapshot> findByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);
}
