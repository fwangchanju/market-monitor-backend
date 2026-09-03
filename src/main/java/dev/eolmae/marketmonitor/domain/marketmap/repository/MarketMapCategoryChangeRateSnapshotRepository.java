package dev.eolmae.marketmonitor.domain.marketmap.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategoryChangeRateSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMapCategoryChangeRateSnapshotRepository
        extends JpaRepository<MarketMapCategoryChangeRateSnapshot, Long>,
                MarketMapCategoryChangeRateSnapshotRepositoryCustom {

    List<MarketMapCategoryChangeRateSnapshot> findByMarketTypeInAndSnapshotTime(List<Market> markets, LocalDateTime snapshotTime);

    void deleteByCategoryIdIn(List<Long> categoryIds);
}
