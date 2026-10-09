package dev.eolmae.marketry.domain.stock.repository;

import static dev.eolmae.marketry.domain.stock.entity.QSectorPriceSnapshot.sectorPriceSnapshot;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.common.enums.Market;
import java.time.LocalDate;
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
    public List<MarketSnapshotTime> findLatestMarketSnapshotTimesBetween(
            LocalDateTime from, LocalDateTime toExclusive) {
        var latestTime = sectorPriceSnapshot.snapshotTime.max();
        return queryFactory
                .select(sectorPriceSnapshot.marketType, latestTime)
                .from(sectorPriceSnapshot)
                .where(sectorPriceSnapshot.snapshotTime.goe(from).and(sectorPriceSnapshot.snapshotTime.lt(toExclusive)))
                .groupBy(sectorPriceSnapshot.marketType)
                .fetch()
                .stream()
                .map(tuple -> new MarketSnapshotTime(tuple.get(sectorPriceSnapshot.marketType), tuple.get(latestTime)))
                .toList();
    }

    @Override
    public List<MarketSnapshotTime> findLatestMarketSnapshotTimesPerDay(List<TimeWindow> windows) {
        BooleanBuilder inWindows = new BooleanBuilder();
        for (TimeWindow window : windows) {
            inWindows.or(sectorPriceSnapshot
                    .snapshotTime
                    .goe(window.from())
                    .and(sectorPriceSnapshot.snapshotTime.lt(window.toExclusive())));
        }
        var latestTime = sectorPriceSnapshot.snapshotTime.max();
        return queryFactory
                .select(sectorPriceSnapshot.marketType, latestTime)
                .from(sectorPriceSnapshot)
                .where(inWindows)
                .groupBy(
                        sectorPriceSnapshot.marketType,
                        sectorPriceSnapshot.snapshotTime.year(),
                        sectorPriceSnapshot.snapshotTime.month(),
                        sectorPriceSnapshot.snapshotTime.dayOfMonth())
                .fetch()
                .stream()
                .map(tuple -> new MarketSnapshotTime(tuple.get(sectorPriceSnapshot.marketType), tuple.get(latestTime)))
                .toList();
    }

    @Override
    public long deleteSnapshotsForDate(LocalDate date, List<MarketSnapshotTime> retainedSnapshotTimes) {
        if (retainedSnapshotTimes.isEmpty()) {
            return 0;
        }
        BooleanExpression targets = null;
        // 시장별 보존 시각이 다를 수 있어 동적 조건을 QueryDSL로 묶는다. 후보 없는 시장은 전체 보존한다.
        for (MarketSnapshotTime retained : retainedSnapshotTimes) {
            BooleanExpression group = sectorPriceSnapshot
                    .marketType
                    .eq(retained.market())
                    .and(sectorPriceSnapshot.snapshotTime.ne(retained.snapshotTime()));
            targets = targets == null ? group : targets.or(group);
        }
        return queryFactory
                .delete(sectorPriceSnapshot)
                .where(
                        sectorPriceSnapshot.snapshotTime.goe(date.atStartOfDay()),
                        sectorPriceSnapshot.snapshotTime.lt(date.plusDays(1).atStartOfDay()),
                        targets)
                .execute();
    }
}
