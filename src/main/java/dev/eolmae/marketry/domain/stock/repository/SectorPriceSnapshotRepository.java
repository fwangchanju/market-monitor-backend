package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorPriceSnapshotRepository
        extends JpaRepository<SectorPriceSnapshot, Long>, SectorPriceSnapshotRepositoryCustom {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);

    List<SectorPriceSnapshot> findByMarketTypeInAndSnapshotTime(List<Market> markets, LocalDateTime snapshotTime);

    /** [from, toExclusive) 구간에 있는 스냅샷 중 가장 늦은 시각의 행 하나 — 종가 윈도우의 latest를 찾는 데 쓴다. */
    Optional<SectorPriceSnapshot> findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
            LocalDateTime from, LocalDateTime toExclusive);
}
