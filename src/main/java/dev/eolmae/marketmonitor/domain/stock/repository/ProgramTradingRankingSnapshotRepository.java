package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRankingType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProgramTradingRankingSnapshotRepository extends JpaRepository<ProgramTradingRankingSnapshot, Long> {

    boolean existsBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyType(
            LocalDateTime snapshotTime, Market market, ProgramRankingType ranking, AmtQtyType amtQty);

    @Query("""
        SELECT s FROM ProgramTradingRankingSnapshot s
        WHERE s.marketType IN :markets
          AND s.rankingType = :ranking
          AND s.amtQtyType = :amtQty
          AND s.snapshotTime = (
              SELECT MAX(s2.snapshotTime) FROM ProgramTradingRankingSnapshot s2
              WHERE s2.marketType IN :markets
                AND s2.rankingType = :ranking
                AND s2.amtQtyType = :amtQty
          )
        ORDER BY s.rank ASC
        """)
    List<ProgramTradingRankingSnapshot> findLatestByMarketTypeInAndRankingTypeAndAmtQtyType(
            List<Market> markets, ProgramRankingType ranking, AmtQtyType amtQty);
}
