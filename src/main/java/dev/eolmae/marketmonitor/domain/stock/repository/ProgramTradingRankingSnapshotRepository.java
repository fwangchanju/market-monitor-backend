package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRankingType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramTradingRankingSnapshotRepository extends JpaRepository<ProgramTradingRankingSnapshot, Long> {

    boolean existsBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyType(
            LocalDateTime snapshotTime, Market market, ProgramRankingType ranking, AmtQtyType amtQty);

    Optional<ProgramTradingRankingSnapshot> findFirstByMarketTypeInAndRankingTypeAndAmtQtyTypeOrderBySnapshotTimeDesc(
            List<Market> markets, ProgramRankingType ranking, AmtQtyType amtQty);

    List<ProgramTradingRankingSnapshot> findByMarketTypeInAndRankingTypeAndAmtQtyTypeAndSnapshotTimeOrderByRankAsc(
            List<Market> markets, ProgramRankingType ranking, AmtQtyType amtQty, LocalDateTime snapshotTime);
}
