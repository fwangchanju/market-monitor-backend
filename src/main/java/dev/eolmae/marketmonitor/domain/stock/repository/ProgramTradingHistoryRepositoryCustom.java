package dev.eolmae.marketmonitor.domain.stock.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ProgramTradingHistoryRepositoryCustom {

    List<LocalDateTime> findSnapshotTimesByStockCodeAndDate(String stockCode, LocalDate date);
}
