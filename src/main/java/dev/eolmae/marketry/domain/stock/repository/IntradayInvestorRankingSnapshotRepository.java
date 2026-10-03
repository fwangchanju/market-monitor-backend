package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.*;
import dev.eolmae.marketry.domain.stock.enums.AmtQty;
import dev.eolmae.marketry.domain.stock.enums.IntradayInvestor;
import dev.eolmae.marketry.domain.stock.enums.IntradayRanking;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntradayInvestorRankingSnapshotRepository
        extends JpaRepository<IntradayInvestorRankingSnapshot, Long> {

    boolean existsBySnapshotTimeAndMarketTypeAndInvestorAndRankingType(
            LocalDateTime snapshotTime, Market market, IntradayInvestor investor, IntradayRanking ranking);

    Optional<IntradayInvestorRankingSnapshot>
            findFirstByMarketTypeInAndInvestorInAndRankingTypeAndAmtQtyOrderBySnapshotTimeDesc(
                    List<Market> markets, List<IntradayInvestor> investors, IntradayRanking ranking, AmtQty amtQty);

    List<IntradayInvestorRankingSnapshot>
            findByMarketTypeInAndInvestorInAndRankingTypeAndAmtQtyAndSnapshotTimeOrderByRankAsc(
                    List<Market> markets,
                    List<IntradayInvestor> investors,
                    IntradayRanking ranking,
                    AmtQty amtQty,
                    LocalDateTime snapshotTime);
}
