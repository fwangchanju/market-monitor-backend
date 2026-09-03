package dev.eolmae.marketmonitor.domain.marketmap.repository;

import static dev.eolmae.marketmonitor.domain.marketmap.entity.QMarketMapCategoryChangeRateSnapshot.marketMapCategoryChangeRateSnapshot;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.common.enums.Market;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MarketMapCategoryChangeRateSnapshotRepositoryImpl implements MarketMapCategoryChangeRateSnapshotRepositoryCustom {

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
}
