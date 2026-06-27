package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRankingType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IntradayInvestorRankingSnapshotRepository extends JpaRepository<IntradayInvestorRankingSnapshot, Long> {

    List<IntradayInvestorRankingSnapshot> findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
            LocalDateTime snapshotTime,
            Market market,
            IntradayInvestorType investor,
            IntradayRankingType ranking);

    @Query("""
        SELECT s FROM IntradayInvestorRankingSnapshot s
        WHERE s.marketType IN :markets
          AND s.investorType IN :investors
          AND s.rankingType = :ranking
          AND s.amtQtyType = :amtQty
          AND s.snapshotTime = (
              SELECT MAX(s2.snapshotTime) FROM IntradayInvestorRankingSnapshot s2
              WHERE s2.marketType IN :markets
                AND s2.investorType IN :investors
                AND s2.rankingType = :ranking
                AND s2.amtQtyType = :amtQty
          )
        ORDER BY s.rank ASC
        """)
    List<IntradayInvestorRankingSnapshot> findLatestByMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyType(
            List<Market> markets,
            List<IntradayInvestorType> investors,
            IntradayRankingType ranking,
            AmtQtyType amtQty);
}
