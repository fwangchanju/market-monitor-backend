package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorPriceSnapshotRepository extends JpaRepository<SectorPriceSnapshot, Long> {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);
}
