package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorPriceSnapshotRepository
        extends JpaRepository<SectorPriceSnapshot, Long>, SectorPriceSnapshotRepositoryCustom {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);

    List<SectorPriceSnapshot> findByMarketTypeInAndSnapshotTime(List<Market> markets, LocalDateTime snapshotTime);
}
