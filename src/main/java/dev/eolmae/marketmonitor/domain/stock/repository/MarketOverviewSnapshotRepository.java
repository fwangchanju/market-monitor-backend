package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketOverviewSnapshotRepository extends JpaRepository<MarketOverviewSnapshot, Long> {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);

    @Query("""
        SELECT s FROM MarketOverviewSnapshot s
        WHERE s.snapshotTime = (SELECT MAX(s2.snapshotTime) FROM MarketOverviewSnapshot s2)
        ORDER BY s.marketType ASC
        """)
    List<MarketOverviewSnapshot> findLatestOrderByMarketTypeAsc();
}
