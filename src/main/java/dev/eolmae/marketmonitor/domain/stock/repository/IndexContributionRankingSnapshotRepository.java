package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IndexContributionRankingSnapshotRepository extends JpaRepository<IndexContributionRankingSnapshot, Long> {

    List<IndexContributionRankingSnapshot> findBySnapshotTimeAndMarketTypeOrderByRankAsc(
            LocalDateTime snapshotTime, Market market);

    @Query("""
        SELECT s FROM IndexContributionRankingSnapshot s
        WHERE s.marketType = :market
          AND s.snapshotTime = (
              SELECT MAX(s2.snapshotTime) FROM IndexContributionRankingSnapshot s2
              WHERE s2.marketType = :market
          )
        ORDER BY s.rank ASC
        """)
    List<IndexContributionRankingSnapshot> findLatestByMarketTypeOrderByRankAsc(Market market);
}
