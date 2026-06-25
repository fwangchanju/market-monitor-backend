package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRankingType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IntradayInvestorRankingSnapshotRepository
        extends JpaRepository<IntradayInvestorRankingSnapshot, Long> {

    List<IntradayInvestorRankingSnapshot> findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
            LocalDateTime snapshotTime,
            Market marketType,
            IntradayInvestorType investorType,
            IntradayRankingType rankingType);

    List<IntradayInvestorRankingSnapshot>
            findBySnapshotTimeAndMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
                    LocalDateTime snapshotTime,
                    Collection<Market> marketTypes,
                    Collection<IntradayInvestorType> investorTypes,
                    IntradayRankingType rankingType,
                    AmtQtyType amtQtyType);

    @Query("SELECT MAX(s.snapshotTime) FROM IntradayInvestorRankingSnapshot s")
    Optional<LocalDateTime> findLatestSnapshotTime();
}
