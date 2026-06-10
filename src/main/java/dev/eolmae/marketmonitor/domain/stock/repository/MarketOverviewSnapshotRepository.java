package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.domain.stock.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketOverviewSnapshotRepository extends JpaRepository<MarketOverviewSnapshot, Long> {

    Optional<MarketOverviewSnapshot> findByMarketTypeAndSnapshotTime(Exchange marketType, LocalDateTime snapshotTime);

    List<MarketOverviewSnapshot> findBySnapshotTimeOrderByMarketTypeAsc(LocalDateTime snapshotTime);

    Optional<MarketOverviewSnapshot> findTopByMarketTypeOrderBySnapshotTimeDesc(Exchange marketType);

    @Query("SELECT MAX(s.snapshotTime) FROM MarketOverviewSnapshot s")
    Optional<LocalDateTime> findLatestSnapshotTime();
}
