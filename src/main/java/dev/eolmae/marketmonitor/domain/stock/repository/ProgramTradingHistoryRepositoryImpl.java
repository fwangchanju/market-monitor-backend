package dev.eolmae.marketmonitor.domain.stock.repository;

import static dev.eolmae.marketmonitor.domain.stock.entity.QProgramTradingHistory.programTradingHistory;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingHistory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProgramTradingHistoryRepositoryImpl implements ProgramTradingHistoryRepositoryCustom {

    private static final int RECENT_LIMIT = 20;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<LocalDateTime> findSnapshotTimesByStockCodeAndDate(String stockCode, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        return queryFactory
                .select(programTradingHistory.snapshotTime)
                .from(programTradingHistory)
                .where(
                        programTradingHistory.stockCode.eq(stockCode),
                        programTradingHistory.snapshotTime.goe(startOfDay),
                        programTradingHistory.snapshotTime.lt(endOfDay))
                .fetch();
    }

    @Override
    public List<ProgramTradingHistory> findRecentByStockCode(String stockCode) {
        return queryFactory
                .selectFrom(programTradingHistory)
                .where(programTradingHistory.stockCode.eq(stockCode))
                .orderBy(programTradingHistory.snapshotTime.desc())
                .limit(RECENT_LIMIT)
                .fetch();
    }
}
