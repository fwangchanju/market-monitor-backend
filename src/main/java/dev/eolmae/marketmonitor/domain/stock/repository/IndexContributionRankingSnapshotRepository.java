package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexContributionRankingSnapshotRepository
        extends JpaRepository<IndexContributionRankingSnapshot, Long> {

    boolean existsBySnapshotTimeAndMarketType(LocalDateTime snapshotTime, Market market);

    Optional<IndexContributionRankingSnapshot> findFirstByMarketTypeOrderBySnapshotTimeDesc(Market market);

    List<IndexContributionRankingSnapshot> findByMarketTypeAndSnapshotTimeOrderByRankAsc(
            Market market, LocalDateTime snapshotTime);
}
