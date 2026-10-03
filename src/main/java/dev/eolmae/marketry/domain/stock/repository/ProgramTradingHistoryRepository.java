package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.*;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramTradingHistoryRepository
        extends JpaRepository<ProgramTradingHistory, Long>, ProgramTradingHistoryRepositoryCustom {

    boolean existsByStockCodeAndSnapshotTime(String stockCode, LocalDateTime snapshotTime);
}
