package dev.eolmae.marketmonitor.domain.marketmap.repository;

import static dev.eolmae.marketmonitor.domain.marketmap.entity.QMarketMapCategoryChangeRateSnapshot.marketMapCategoryChangeRateSnapshot;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.common.enums.Market;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
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
    public List<MarketSnapshotTime> findMarketSnapshotTimesInWindow(
            LocalDateTime cutoff, LocalTime windowStart, LocalTime windowEnd) {
        var snapshot = marketMapCategoryChangeRateSnapshot;
        return queryFactory
                .select(snapshot.marketType, snapshot.snapshotTime)
                .distinct()
                .from(snapshot)
                .where(snapshot.snapshotTime.before(cutoff).and(inWindow(windowStart, windowEnd)))
                .fetch()
                .stream()
                .map(tuple -> new MarketSnapshotTime(tuple.get(snapshot.marketType), tuple.get(snapshot.snapshotTime)))
                .toList();
    }

    @Override
    public long deleteSnapshotsBefore(LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes) {
        return queryFactory
                .delete(marketMapCategoryChangeRateSnapshot)
                .where(targetPredicate(cutoff, retainedSnapshotTimes))
                .execute();
    }

    @Override
    public SnapshotRetentionSummary summarizeSnapshotsToDelete(
            LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes, int sampleSize) {
        var snapshot = marketMapCategoryChangeRateSnapshot;
        BooleanExpression targetCondition = targetPredicate(cutoff, retainedSnapshotTimes);

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

        List<MarketSnapshotTime> retainedSampleSnapshotTimes = retainedSnapshotTimes.stream()
                .sorted(Comparator.comparing(MarketSnapshotTime::snapshotTime).reversed())
                .limit(sampleSize)
                .toList();
        int retainedDateCount = (int) retainedSnapshotTimes.stream()
                .map(retained -> retained.snapshotTime().toLocalDate())
                .distinct()
                .count();

        return new SnapshotRetentionSummary(
                aggregate.get(snapshot.count()),
                totalCountBeforeCutoff,
                aggregate.get(snapshot.snapshotTime.min()),
                aggregate.get(snapshot.snapshotTime.max()),
                sampleSnapshotTimes,
                retainedSampleSnapshotTimes,
                retainedDateCount);
    }

    // cutoff 이전이면서 retainedSnapshotTimes(순수 자바 함수가 보존 윈도우에서 고른 (마켓,시각))에 없는 행 —
    // 삭제/조회 양쪽에서 동일하게 쓰는 술어. retainedSnapshotTimes가 비면(그 구간에 데이터가 아예 없던
    // 날) cutoff 조건만 남아 그 구간 전체가 삭제 대상이 된다 — 의도된 동작이다.
    private BooleanExpression targetPredicate(LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes) {
        var snapshot = marketMapCategoryChangeRateSnapshot;
        BooleanExpression cutoffCondition = snapshot.snapshotTime.before(cutoff);
        BooleanExpression retainedCondition = retainedPredicate(retainedSnapshotTimes);
        if (retainedCondition == null) {
            return cutoffCondition;
        }
        return cutoffCondition.and(retainedCondition.not());
    }

    private BooleanExpression retainedPredicate(List<MarketSnapshotTime> retainedSnapshotTimes) {
        var snapshot = marketMapCategoryChangeRateSnapshot;
        BooleanExpression matched = null;
        for (MarketSnapshotTime retained : retainedSnapshotTimes) {
            BooleanExpression term =
                    snapshot.marketType.eq(retained.market()).and(snapshot.snapshotTime.eq(retained.snapshotTime()));
            matched = matched == null ? term : matched.or(term);
        }
        return matched;
    }

    // 윈도우 [windowStart, windowEnd) — 두 값이 같은 시(hour)라는 가정 없이 하루 중 분(分) 단위로 비교한다.
    private BooleanExpression inWindow(LocalTime windowStart, LocalTime windowEnd) {
        var snapshot = marketMapCategoryChangeRateSnapshot;
        NumberExpression<Integer> minuteOfDay =
                snapshot.snapshotTime.hour().multiply(60).add(snapshot.snapshotTime.minute());
        int startMinuteOfDay = windowStart.getHour() * 60 + windowStart.getMinute();
        int endMinuteOfDay = windowEnd.getHour() * 60 + windowEnd.getMinute();
        return minuteOfDay.goe(startMinuteOfDay).and(minuteOfDay.lt(endMinuteOfDay));
    }
}
