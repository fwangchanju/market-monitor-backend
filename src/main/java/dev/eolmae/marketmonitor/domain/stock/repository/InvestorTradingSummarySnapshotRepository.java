package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.InvestorType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorTradingSummarySnapshotRepository extends JpaRepository<InvestorTradingSummarySnapshot, Long> {

    boolean existsByMarketTypeAndInvestorTypeAndAmtQtyTypeAndSnapshotTime(
            Market market, InvestorType investor, AmtQtyType amtQty, LocalDateTime snapshotTime);

    Optional<InvestorTradingSummarySnapshot> findFirstByOrderBySnapshotTimeDesc();

    List<InvestorTradingSummarySnapshot> findBySnapshotTimeOrderByMarketTypeAscInvestorTypeAsc(LocalDateTime snapshotTime);
}
