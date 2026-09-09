package dev.eolmae.marketmonitor.domain.stock.repository;

import static dev.eolmae.marketmonitor.domain.stock.entity.QSectorPriceSnapshot.sectorPriceSnapshot;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.common.enums.Market;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    public long deleteSnapshotsBefore(LocalDateTime cutoff, LocalTime marketCloseTime) {
        return queryFactory
                .delete(sectorPriceSnapshot)
                .where(targetPredicate(cutoff, marketCloseTime))
                .execute();
    }

    @Override
    public SnapshotRetentionSummary summarizeSnapshotsToDelete(
            LocalDateTime cutoff, LocalTime marketCloseTime, int sampleSize) {
        BooleanExpression targetCondition = targetPredicate(cutoff, marketCloseTime);

        Tuple aggregate = queryFactory
                .select(
                        sectorPriceSnapshot.count(),
                        sectorPriceSnapshot.snapshotTime.min(),
                        sectorPriceSnapshot.snapshotTime.max())
                .from(sectorPriceSnapshot)
                .where(targetCondition)
                .fetchOne();

        long totalCountBeforeCutoff = queryFactory
                .select(sectorPriceSnapshot.count())
                .from(sectorPriceSnapshot)
                .where(sectorPriceSnapshot.snapshotTime.before(cutoff))
                .fetchOne();

        // 삭제 대상에 등장하는 서로 다른 시각(HH:mm)별로 대표 시각을 하나씩 뽑아 표본으로 삼는다.
        List<LocalTime> sampleSnapshotTimes = queryFactory
                .select(sectorPriceSnapshot.snapshotTime.min())
                .from(sectorPriceSnapshot)
                .where(targetCondition)
                .groupBy(sectorPriceSnapshot.snapshotTime.hour(), sectorPriceSnapshot.snapshotTime.minute())
                .limit(sampleSize)
                .fetch()
                .stream()
                .map(LocalDateTime::toLocalTime)
                .toList();

        return new SnapshotRetentionSummary(
                aggregate.get(sectorPriceSnapshot.count()),
                totalCountBeforeCutoff,
                aggregate.get(sectorPriceSnapshot.snapshotTime.min()),
                aggregate.get(sectorPriceSnapshot.snapshotTime.max()),
                sampleSnapshotTimes);
    }

    // cutoff 이전(30일 지남)이면서 marketCloseTime(15:30)이 아닌 스냅샷 — 삭제/조회 양쪽에서 동일하게 쓰는 술어.
    private BooleanExpression targetPredicate(LocalDateTime cutoff, LocalTime marketCloseTime) {
        return sectorPriceSnapshot
                .snapshotTime
                .before(cutoff)
                .and(sectorPriceSnapshot
                        .snapshotTime
                        .hour()
                        .ne(marketCloseTime.getHour())
                        .or(sectorPriceSnapshot.snapshotTime.minute().ne(marketCloseTime.getMinute())));
    }
}
