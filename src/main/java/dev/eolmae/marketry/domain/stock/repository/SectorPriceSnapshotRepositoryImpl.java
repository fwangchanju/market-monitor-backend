package dev.eolmae.marketry.domain.stock.repository;

import static dev.eolmae.marketry.domain.stock.entity.QMarketCalendar.marketCalendar;
import static dev.eolmae.marketry.domain.stock.entity.QSectorPriceSnapshot.sectorPriceSnapshot;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SectorPriceSnapshotRepositoryImpl implements SectorPriceSnapshotRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets, LocalDate today) {
        LocalDateTime latest = queryFactory
                .select(sectorPriceSnapshot.snapshotTime)
                .from(sectorPriceSnapshot)
                .where(
                        sectorPriceSnapshot.marketType.in(markets),
                        sectorPriceSnapshot.snapshotTime.lt(today.plusDays(1).atStartOfDay()),
                        JPAExpressions.selectOne()
                                .from(marketCalendar)
                                .where(
                                        marketCalendar.country.eq(Country.KR),
                                        marketCalendar.status.eq(MarketCalendarStatus.TRADING_DAY),
                                        matchesCalendarDate())
                                .exists())
                .groupBy(sectorPriceSnapshot.snapshotTime)
                .having(sectorPriceSnapshot.marketType.countDistinct().eq((long) markets.size()))
                .orderBy(sectorPriceSnapshot.snapshotTime.desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(latest);
    }

    @Override
    public List<LocalDate> findTradingSnapshotDates(LocalDate today) {
        var latest = sectorPriceSnapshot.snapshotTime.max();
        return queryFactory
                .select(latest)
                .from(sectorPriceSnapshot)
                .where(
                        sectorPriceSnapshot.snapshotTime.lt(today.plusDays(1).atStartOfDay()),
                        JPAExpressions.selectOne()
                                .from(marketCalendar)
                                .where(
                                        marketCalendar.country.eq(Country.KR),
                                        marketCalendar.status.eq(MarketCalendarStatus.TRADING_DAY),
                                        matchesCalendarDate())
                                .exists())
                .groupBy(
                        sectorPriceSnapshot.snapshotTime.year(),
                        sectorPriceSnapshot.snapshotTime.month(),
                        sectorPriceSnapshot.snapshotTime.dayOfMonth())
                .orderBy(latest.desc())
                .fetch()
                .stream()
                .map(LocalDateTime::toLocalDate)
                .toList();
    }

    private BooleanExpression matchesCalendarDate() {
        return marketCalendar
                .date
                .year()
                .eq(sectorPriceSnapshot.snapshotTime.year())
                .and(marketCalendar.date.month().eq(sectorPriceSnapshot.snapshotTime.month()))
                .and(marketCalendar.date.dayOfMonth().eq(sectorPriceSnapshot.snapshotTime.dayOfMonth()));
    }

    @Override
    public List<LocalDate> findMultipleSnapshotDates(LocalDate throughDate) {
        var latest = sectorPriceSnapshot.snapshotTime.max();
        return queryFactory
                .select(latest)
                .from(sectorPriceSnapshot)
                .where(
                        sectorPriceSnapshot.snapshotTime.lt(
                                throughDate.plusDays(1).atStartOfDay()),
                        JPAExpressions.selectOne()
                                .from(marketCalendar)
                                .where(
                                        marketCalendar.country.eq(Country.KR),
                                        marketCalendar.status.eq(MarketCalendarStatus.TRADING_DAY),
                                        matchesCalendarDate())
                                .exists())
                .groupBy(
                        sectorPriceSnapshot.snapshotTime.year(),
                        sectorPriceSnapshot.snapshotTime.month(),
                        sectorPriceSnapshot.snapshotTime.dayOfMonth(),
                        sectorPriceSnapshot.marketType)
                .having(sectorPriceSnapshot.snapshotTime.countDistinct().gt(1L))
                .orderBy(latest.asc())
                .fetch()
                .stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .toList();
    }

    @Override
    public List<SnapshotDaySummary> findSnapshotDaySummaries(List<TimeWindow> windows) {
        if (windows.isEmpty()) {
            return List.of();
        }
        BooleanBuilder dates = new BooleanBuilder();
        BooleanBuilder beforeAfterHours = new BooleanBuilder();
        for (TimeWindow window : windows) {
            LocalDateTime start = window.from().toLocalDate().atStartOfDay();
            BooleanExpression inDay = sectorPriceSnapshot
                    .snapshotTime
                    .goe(start)
                    .and(sectorPriceSnapshot.snapshotTime.lt(start.plusDays(1)));
            dates.or(inDay);
            beforeAfterHours.or(inDay.and(sectorPriceSnapshot.snapshotTime.lt(window.toExclusive())));
        }
        var latest = sectorPriceSnapshot.snapshotTime.max();
        var count = sectorPriceSnapshot.snapshotTime.countDistinct();
        var before = new CaseBuilder()
                .when(beforeAfterHours)
                .then(sectorPriceSnapshot.snapshotTime)
                .otherwise(Expressions.nullExpression(LocalDateTime.class))
                .max();
        return queryFactory
                .select(sectorPriceSnapshot.marketType, latest, count, before)
                .from(sectorPriceSnapshot)
                .where(dates)
                .groupBy(
                        sectorPriceSnapshot.marketType,
                        sectorPriceSnapshot.snapshotTime.year(),
                        sectorPriceSnapshot.snapshotTime.month(),
                        sectorPriceSnapshot.snapshotTime.dayOfMonth())
                .fetch()
                .stream()
                .map(tuple -> new SnapshotDaySummary(
                        tuple.get(sectorPriceSnapshot.marketType),
                        tuple.get(latest),
                        tuple.get(count),
                        tuple.get(before)))
                .toList();
    }

    @Override
    public List<SectorPriceSnapshot> findByMarketSnapshotTimes(List<MarketSnapshotTime> times) {
        if (times.isEmpty()) {
            return List.of();
        }
        BooleanBuilder selected = new BooleanBuilder();
        for (MarketSnapshotTime time : times) {
            selected.or(sectorPriceSnapshot
                    .marketType
                    .eq(time.market())
                    .and(sectorPriceSnapshot.snapshotTime.eq(time.snapshotTime())));
        }
        return queryFactory.selectFrom(sectorPriceSnapshot).where(selected).fetch();
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
