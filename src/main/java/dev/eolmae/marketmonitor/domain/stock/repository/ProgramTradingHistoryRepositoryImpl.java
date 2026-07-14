package dev.eolmae.marketmonitor.domain.stock.repository;

import static dev.eolmae.marketmonitor.domain.stock.entity.QProgramTradingHistory.programTradingHistory;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProgramTradingHistoryRepositoryImpl implements ProgramTradingHistoryRepositoryCustom {

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
}
