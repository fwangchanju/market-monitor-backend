package dev.eolmae.marketmonitor.domain.marketmap.repository;

import static dev.eolmae.marketmonitor.domain.marketmap.entity.QMarketMapCategoryChangeRateSnapshot.marketMapCategoryChangeRateSnapshot;

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
public class MarketMapCategoryChangeRateSnapshotRepositoryImpl
        implements MarketMapCategoryChangeRateSnapshotRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets) {
        var snapshot = marketMapCategoryChangeRateSnapshot;
        // markets에 속하는 행들을 시각별로 묶은 뒤, 그 시각에 markets 전부가 존재하는(distinct marketType
        // 개수가 markets 크기와 같은) 시각만 남기고 그중 가장 최근 걸 고른다.
        LocalDateTime latest = queryFactory
                .select(snapshot.snapshotTime)
                .from(snapshot)
                .where(snapshot.marketType.in(markets))
                .groupBy(snapshot.snapshotTime)
                .having(snapshot.marketType.countDistinct().eq((long) markets.size()))
                .orderBy(snapshot.snapshotTime.desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(latest);
    }

    @Override
    public long deleteSnapshotsBefore(LocalDateTime cutoff, LocalTime marketCloseTime) {
        return queryFactory
                .delete(marketMapCategoryChangeRateSnapshot)
                .where(targetPredicate(cutoff, marketCloseTime))
                .execute();
    }

    @Override
    public SnapshotRetentionSummary summarizeSnapshotsToDelete(
            LocalDateTime cutoff, LocalTime marketCloseTime, int sampleSize) {
        var snapshot = marketMapCategoryChangeRateSnapshot;
        BooleanExpression targetCondition = targetPredicate(cutoff, marketCloseTime);

        Tuple aggregate = queryFactory
                .select(snapshot.count(), snapshot.snapshotTime.min(), snapshot.snapshotTime.max())
                .from(snapshot)
                .where(targetCondition)
                .fetchOne();

        long totalCountBeforeCutoff = queryFactory
                .select(snapshot.count())
                .from(snapshot)
                .where(snapshot.snapshotTime.before(cutoff))
                .fetchOne();

        // 삭제 대상에 등장하는 서로 다른 시각(HH:mm)별로 대표 시각을 하나씩 뽑아 표본으로 삼는다.
        List<LocalTime> sampleSnapshotTimes = queryFactory
                .select(snapshot.snapshotTime.min())
                .from(snapshot)
                .where(targetCondition)
                .groupBy(snapshot.snapshotTime.hour(), snapshot.snapshotTime.minute())
                .limit(sampleSize)
                .fetch()
                .stream()
                .map(LocalDateTime::toLocalTime)
                .toList();

        return new SnapshotRetentionSummary(
                aggregate.get(snapshot.count()),
                totalCountBeforeCutoff,
                aggregate.get(snapshot.snapshotTime.min()),
                aggregate.get(snapshot.snapshotTime.max()),
                sampleSnapshotTimes);
    }

    // cutoff 이전(30일 지남)이면서 marketCloseTime(15:30)이 아닌 스냅샷 — 삭제/조회 양쪽에서 동일하게 쓰는 술어.
    private BooleanExpression targetPredicate(LocalDateTime cutoff, LocalTime marketCloseTime) {
        var snapshot = marketMapCategoryChangeRateSnapshot;
        return snapshot.snapshotTime
                .before(cutoff)
                .and(snapshot.snapshotTime
                        .hour()
                        .ne(marketCloseTime.getHour())
                        .or(snapshot.snapshotTime.minute().ne(marketCloseTime.getMinute())));
    }
}
