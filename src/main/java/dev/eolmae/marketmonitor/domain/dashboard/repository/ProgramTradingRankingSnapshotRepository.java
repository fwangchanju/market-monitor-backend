package dev.eolmae.marketmonitor.domain.dashboard.repository;
import dev.eolmae.marketmonitor.domain.dashboard.*;

import dev.eolmae.marketmonitor.common.enums.AmtQtyType;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.ProgramRankingType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProgramTradingRankingSnapshotRepository extends JpaRepository<ProgramTradingRankingSnapshot, Long> {

	List<ProgramTradingRankingSnapshot> findBySnapshotTimeAndRankingTypeOrderByRankAsc(
		LocalDateTime snapshotTime,
		ProgramRankingType rankingType
	);

	boolean existsBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyType(
		LocalDateTime snapshotTime,
		Exchange marketType,
		ProgramRankingType rankingType,
		AmtQtyType amtQtyType
	);

	List<ProgramTradingRankingSnapshot> findBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
		LocalDateTime snapshotTime,
		Exchange marketType,
		ProgramRankingType rankingType,
		AmtQtyType amtQtyType
	);

	@Query("SELECT MAX(s.snapshotTime) FROM ProgramTradingRankingSnapshot s")
	Optional<LocalDateTime> findLatestSnapshotTime();
}
