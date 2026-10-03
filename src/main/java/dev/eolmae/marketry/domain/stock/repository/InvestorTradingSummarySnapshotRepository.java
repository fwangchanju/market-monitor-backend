package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.*;
import dev.eolmae.marketry.domain.stock.enums.AmtQty;
import dev.eolmae.marketry.domain.stock.enums.Investor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorTradingSummarySnapshotRepository extends JpaRepository<InvestorTradingSummarySnapshot, Long> {

    boolean existsByMarketTypeAndInvestorAndAmtQtyAndSnapshotTime(
            Market market, Investor investor, AmtQty amtQty, LocalDateTime snapshotTime);

    Optional<InvestorTradingSummarySnapshot> findFirstByOrderBySnapshotTimeDesc();

    List<InvestorTradingSummarySnapshot> findBySnapshotTime(LocalDateTime snapshotTime);
}
