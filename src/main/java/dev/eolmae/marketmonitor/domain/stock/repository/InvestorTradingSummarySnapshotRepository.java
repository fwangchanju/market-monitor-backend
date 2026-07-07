package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.InvestorType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvestorTradingSummarySnapshotRepository extends JpaRepository<InvestorTradingSummarySnapshot, Long> {

    boolean existsByMarketTypeAndInvestorTypeAndAmtQtyTypeAndSnapshotTime(
            Market market, InvestorType investor, AmtQtyType amtQty, LocalDateTime snapshotTime);

    @Query("""
        SELECT s FROM InvestorTradingSummarySnapshot s
        WHERE s.snapshotTime = (SELECT MAX(s2.snapshotTime) FROM InvestorTradingSummarySnapshot s2)
        ORDER BY s.marketType ASC, s.investorType ASC
        """)
    List<InvestorTradingSummarySnapshot> findLatestOrderByMarketTypeAscInvestorTypeAsc();
}
