package dev.eolmae.marketmonitor.domain.dashboard.repository;

import dev.eolmae.marketmonitor.common.enums.AmtQtyType;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.InvestorType;
import dev.eolmae.marketmonitor.domain.dashboard.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvestorTradingSummarySnapshotRepository extends JpaRepository<InvestorTradingSummarySnapshot, Long> {

    Optional<InvestorTradingSummarySnapshot> findByMarketTypeAndInvestorTypeAndAmtQtyTypeAndSnapshotTime(
            Exchange marketType, InvestorType investorType, AmtQtyType amtQtyType, LocalDateTime snapshotTime);

    List<InvestorTradingSummarySnapshot> findBySnapshotTimeOrderByMarketTypeAscInvestorTypeAsc(
            LocalDateTime snapshotTime);

    @Query("SELECT MAX(s.snapshotTime) FROM InvestorTradingSummarySnapshot s")
    Optional<LocalDateTime> findLatestSnapshotTime();
}
