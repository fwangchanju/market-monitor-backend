package dev.eolmae.marketmonitor.domain.stock.repository;

import static dev.eolmae.marketmonitor.domain.stock.entity.QProgramTradingDailyHistory.programTradingDailyHistory;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingDailyHistory;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProgramTradingDailyHistoryRepositoryImpl implements ProgramTradingDailyHistoryRepositoryCustom {

    private static final int RECENT_LIMIT = 20;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ProgramTradingDailyHistory> findRecentByStockCode(String stockCode) {
        return queryFactory
                .selectFrom(programTradingDailyHistory)
                .where(programTradingDailyHistory.stockCode.eq(stockCode))
                .orderBy(programTradingDailyHistory.tradeDate.desc())
                .limit(RECENT_LIMIT)
                .fetch();
    }
}
