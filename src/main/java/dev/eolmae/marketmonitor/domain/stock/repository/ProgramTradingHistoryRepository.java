package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramTradingHistoryRepository
        extends JpaRepository<ProgramTradingHistory, Long>, ProgramTradingHistoryRepositoryCustom {

    boolean existsByStockCodeAndSnapshotTime(String stockCode, LocalDateTime snapshotTime);

    List<ProgramTradingHistory> findByStockCodeAndSnapshotTimeBetweenOrderBySnapshotTimeAsc(
            String stockCode, LocalDateTime from, LocalDateTime to);
}
