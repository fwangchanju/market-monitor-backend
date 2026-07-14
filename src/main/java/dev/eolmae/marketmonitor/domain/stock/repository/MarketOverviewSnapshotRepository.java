package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketOverviewSnapshotRepository extends JpaRepository<MarketOverviewSnapshot, Long> {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);

    Optional<MarketOverviewSnapshot> findFirstByOrderBySnapshotTimeDesc();

    List<MarketOverviewSnapshot> findBySnapshotTimeOrderByMarketTypeAsc(LocalDateTime snapshotTime);
}
