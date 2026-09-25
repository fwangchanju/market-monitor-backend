package dev.eolmae.marketmonitor.domain.stock.repository;

import static dev.eolmae.marketmonitor.domain.stock.entity.QSectorPriceSnapshot.sectorPriceSnapshot;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
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
    public List<MarketSnapshotTime> findMarketSnapshotTimesInWindow(
            LocalDateTime cutoff, LocalTime windowStart, LocalTime windowEnd) {
        return queryFactory
                .select(sectorPriceSnapshot.marketType, sectorPriceSnapshot.snapshotTime)
                .distinct()
                .from(sectorPriceSnapshot)
                .where(sectorPriceSnapshot.snapshotTime.before(cutoff).and(inWindow(windowStart, windowEnd)))
                .fetch()
                .stream()
                .map(tuple -> new MarketSnapshotTime(
                        tuple.get(sectorPriceSnapshot.marketType), tuple.get(sectorPriceSnapshot.snapshotTime)))
                .toList();
    }

    @Override
    public long deleteSnapshotsBefore(LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes) {
        return queryFactory
                .delete(sectorPriceSnapshot)
                .where(targetPredicate(cutoff, retainedSnapshotTimes))
                .execute();
    }

    @Override
    public boolean existsBefore(LocalDateTime cutoff) {
        Integer exists = queryFactory
                .selectOne()
                .from(sectorPriceSnapshot)
                .where(sectorPriceSnapshot.snapshotTime.before(cutoff))
                .fetchFirst();
        return exists != null;
    }

    // cutoff 이전이면서 retainedSnapshotTimes(순수 자바 함수가 보존 윈도우에서 고른 (마켓,시각))에 없는 행 —
    // 삭제/조회 양쪽에서 동일하게 쓰는 술어. retainedSnapshotTimes가 비면(그 구간에 데이터가 아예 없던
    // 날) cutoff 조건만 남아 그 구간 전체가 삭제 대상이 된다 — 의도된 동작이다.
    private BooleanExpression targetPredicate(LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes) {
        BooleanExpression cutoffCondition = sectorPriceSnapshot.snapshotTime.before(cutoff);
        BooleanExpression retainedCondition = retainedPredicate(retainedSnapshotTimes);
        if (retainedCondition == null) {
            return cutoffCondition;
        }
        return cutoffCondition.and(retainedCondition.not());
    }

    private BooleanExpression retainedPredicate(List<MarketSnapshotTime> retainedSnapshotTimes) {
        BooleanExpression matched = null;
        for (MarketSnapshotTime retained : retainedSnapshotTimes) {
            BooleanExpression term = sectorPriceSnapshot
                    .marketType
                    .eq(retained.market())
                    .and(sectorPriceSnapshot.snapshotTime.eq(retained.snapshotTime()));
            matched = matched == null ? term : matched.or(term);
        }
        return matched;
    }

    // 윈도우 [windowStart, windowEnd) — 두 값이 같은 시(hour)라는 가정 없이 하루 중 분(分) 단위로 비교한다.
    private BooleanExpression inWindow(LocalTime windowStart, LocalTime windowEnd) {
        NumberExpression<Integer> minuteOfDay =
                sectorPriceSnapshot.snapshotTime.hour().multiply(60).add(sectorPriceSnapshot.snapshotTime.minute());
        int startMinuteOfDay = windowStart.getHour() * 60 + windowStart.getMinute();
        int endMinuteOfDay = windowEnd.getHour() * 60 + windowEnd.getMinute();
        return minuteOfDay.goe(startMinuteOfDay).and(minuteOfDay.lt(endMinuteOfDay));
    }
}
