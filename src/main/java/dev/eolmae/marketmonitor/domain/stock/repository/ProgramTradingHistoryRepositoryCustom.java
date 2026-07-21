package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingHistory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ProgramTradingHistoryRepositoryCustom {

    List<LocalDateTime> findSnapshotTimesByStockCodeAndDate(String stockCode, LocalDate date);

    List<ProgramTradingHistory> findRecentByStockCode(String stockCode);
}
