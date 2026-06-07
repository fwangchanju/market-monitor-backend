package dev.eolmae.marketmonitor.domain.history.repository;

import dev.eolmae.marketmonitor.domain.history.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramTradingHistoryRepository extends JpaRepository<ProgramTradingHistory, Long> {

    boolean existsByStockCodeAndSnapshotTime(String stockCode, LocalDateTime snapshotTime);

    @Query(
            "SELECT h.snapshotTime FROM ProgramTradingHistory h WHERE h.stockCode = :stockCode AND cast(h.snapshotTime as LocalDate) = :date")
    List<LocalDateTime> findSnapshotTimesByStockCodeAndDate(
            @Param("stockCode") String stockCode, @Param("date") LocalDate date);

    List<ProgramTradingHistory> findByStockCodeAndSnapshotTimeBetweenOrderBySnapshotTimeAsc(
            String stockCode, LocalDateTime from, LocalDateTime to);
}
