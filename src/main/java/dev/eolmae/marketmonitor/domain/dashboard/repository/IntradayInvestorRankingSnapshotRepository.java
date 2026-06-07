package dev.eolmae.marketmonitor.domain.dashboard.repository;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.common.enums.IntradayRankingType;
import dev.eolmae.marketmonitor.domain.dashboard.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IntradayInvestorRankingSnapshotRepository
        extends JpaRepository<IntradayInvestorRankingSnapshot, Long> {

    List<IntradayInvestorRankingSnapshot> findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
            LocalDateTime snapshotTime,
            Exchange marketType,
            IntradayInvestorType investorType,
            IntradayRankingType rankingType);

    @Query("SELECT MAX(s.snapshotTime) FROM IntradayInvestorRankingSnapshot s")
    Optional<LocalDateTime> findLatestSnapshotTime();
}
