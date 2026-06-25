package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRankingType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProgramTradingRankingSnapshotRepository extends JpaRepository<ProgramTradingRankingSnapshot, Long> {

    List<ProgramTradingRankingSnapshot> findBySnapshotTimeAndRankingTypeOrderByRankAsc(
            LocalDateTime snapshotTime, ProgramRankingType rankingType);

    boolean existsBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyType(
            LocalDateTime snapshotTime, Market marketType, ProgramRankingType rankingType, AmtQtyType amtQtyType);

    List<ProgramTradingRankingSnapshot> findBySnapshotTimeAndMarketTypeInAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
            LocalDateTime snapshotTime, List<Market> marketTypes, ProgramRankingType rankingType, AmtQtyType amtQtyType);

    @Query("SELECT MAX(s.snapshotTime) FROM ProgramTradingRankingSnapshot s")
    Optional<LocalDateTime> findLatestSnapshotTime();
}
