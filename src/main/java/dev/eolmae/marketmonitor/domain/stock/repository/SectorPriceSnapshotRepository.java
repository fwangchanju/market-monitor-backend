package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorPriceSnapshotRepository
        extends JpaRepository<SectorPriceSnapshot, Long>, SectorPriceSnapshotRepositoryCustom {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);

    List<SectorPriceSnapshot> findByMarketTypeInAndSnapshotTime(List<Market> markets, LocalDateTime snapshotTime);
}
