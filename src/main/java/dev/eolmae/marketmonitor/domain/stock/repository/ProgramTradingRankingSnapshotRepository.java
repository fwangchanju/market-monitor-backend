package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQty;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRanking;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramTradingRankingSnapshotRepository extends JpaRepository<ProgramTradingRankingSnapshot, Long> {

    boolean existsBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQty(
            LocalDateTime snapshotTime, Market market, ProgramRanking ranking, AmtQty amtQty);

    Optional<ProgramTradingRankingSnapshot> findFirstByMarketTypeInAndRankingTypeAndAmtQtyOrderBySnapshotTimeDesc(
            List<Market> markets, ProgramRanking ranking, AmtQty amtQty);

    List<ProgramTradingRankingSnapshot> findByMarketTypeInAndRankingTypeAndAmtQtyAndSnapshotTime(
            List<Market> markets, ProgramRanking ranking, AmtQty amtQty, LocalDateTime snapshotTime);
}
