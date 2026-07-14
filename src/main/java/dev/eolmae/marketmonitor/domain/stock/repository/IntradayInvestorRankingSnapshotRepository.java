package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRankingType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntradayInvestorRankingSnapshotRepository extends JpaRepository<IntradayInvestorRankingSnapshot, Long> {

    boolean existsBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingType(
            LocalDateTime snapshotTime, Market market, IntradayInvestorType investor, IntradayRankingType ranking);

    Optional<IntradayInvestorRankingSnapshot>
            findFirstByMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeOrderBySnapshotTimeDesc(
                    List<Market> markets,
                    List<IntradayInvestorType> investors,
                    IntradayRankingType ranking,
                    AmtQtyType amtQty);

    List<IntradayInvestorRankingSnapshot>
            findByMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeAndSnapshotTimeOrderByRankAsc(
                    List<Market> markets,
                    List<IntradayInvestorType> investors,
                    IntradayRankingType ranking,
                    AmtQtyType amtQty,
                    LocalDateTime snapshotTime);
}
