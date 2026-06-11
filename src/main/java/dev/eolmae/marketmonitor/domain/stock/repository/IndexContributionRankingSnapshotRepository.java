package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IndexContributionRankingSnapshotRepository
        extends JpaRepository<IndexContributionRankingSnapshot, Long> {

    List<IndexContributionRankingSnapshot> findBySnapshotTimeAndMarketTypeOrderByRankAsc(
            LocalDateTime snapshotTime, Market marketType);

    @Query("SELECT MAX(s.snapshotTime) FROM IndexContributionRankingSnapshot s")
    Optional<LocalDateTime> findLatestSnapshotTime();
}
