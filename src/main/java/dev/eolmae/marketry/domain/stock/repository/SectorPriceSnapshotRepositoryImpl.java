package dev.eolmae.marketry.domain.stock.repository;

import static dev.eolmae.marketry.domain.stock.entity.QSectorPriceSnapshot.sectorPriceSnapshot;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.common.enums.Market;
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

    @Override
    public List<MarketSnapshotTime> findMarketSnapshotTimesBefore(LocalDateTime cutoff) {
        return queryFactory
                .select(sectorPriceSnapshot.marketType, sectorPriceSnapshot.snapshotTime)
                .distinct()
                .from(sectorPriceSnapshot)
                .where(sectorPriceSnapshot.snapshotTime.before(cutoff))
                .fetch()
                .stream()
                .map(tuple -> new MarketSnapshotTime(
                        tuple.get(sectorPriceSnapshot.marketType), tuple.get(sectorPriceSnapshot.snapshotTime)))
                .toList();
    }

    @Override
    public long deleteSnapshotsBefore(LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes) {
        if (retainedSnapshotTimes.isEmpty()) {
            return 0;
        }
        BooleanExpression targets = null;
        // 종가가 있는 날짜·마켓만 삭제한다. 후보가 없는 다른 그룹은 전체 보존한다.
        for (MarketSnapshotTime retained : retainedSnapshotTimes) {
            LocalDateTime dayStart = retained.snapshotTime().toLocalDate().atStartOfDay();
            BooleanExpression group = sectorPriceSnapshot
                    .marketType
                    .eq(retained.market())
                    .and(sectorPriceSnapshot.snapshotTime.goe(dayStart))
                    .and(sectorPriceSnapshot.snapshotTime.lt(dayStart.plusDays(1)))
                    .and(sectorPriceSnapshot.snapshotTime.ne(retained.snapshotTime()));
            targets = targets == null ? group : targets.or(group);
        }
        return queryFactory
                .delete(sectorPriceSnapshot)
                .where(sectorPriceSnapshot.snapshotTime.before(cutoff).and(targets))
                .execute();
    }
}
