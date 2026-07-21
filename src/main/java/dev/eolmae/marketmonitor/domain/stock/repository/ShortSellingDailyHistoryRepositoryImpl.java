package dev.eolmae.marketmonitor.domain.stock.repository;

import static dev.eolmae.marketmonitor.domain.stock.entity.QShortSellingDailyHistory.shortSellingDailyHistory;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.domain.stock.entity.ShortSellingDailyHistory;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ShortSellingDailyHistoryRepositoryImpl implements ShortSellingDailyHistoryRepositoryCustom {

    private static final int RECENT_LIMIT = 20;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ShortSellingDailyHistory> findRecentByStockCode(String stockCode) {
        return queryFactory
                .selectFrom(shortSellingDailyHistory)
                .where(shortSellingDailyHistory.stockCode.eq(stockCode))
                .orderBy(shortSellingDailyHistory.tradeDate.desc())
                .limit(RECENT_LIMIT)
                .fetch();
    }
}
