package dev.eolmae.marketmonitor.domain.stock.repository;

import static dev.eolmae.marketmonitor.domain.stock.entity.QSectorPriceSnapshot.sectorPriceSnapshot;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.common.enums.Market;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SectorPriceSnapshotRepositoryImpl implements SectorPriceSnapshotRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets) {
        LocalDateTime latest = queryFactory
                .select(sectorPriceSnapshot.snapshotTime)
                .from(sectorPriceSnapshot)
                .where(sectorPriceSnapshot.marketType.in(markets))
                .groupBy(sectorPriceSnapshot.snapshotTime)
                .having(sectorPriceSnapshot.marketType.countDistinct().eq((long) markets.size()))
                .orderBy(sectorPriceSnapshot.snapshotTime.desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(latest);
    }
}
