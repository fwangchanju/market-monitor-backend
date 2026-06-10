package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramTradingDailyHistoryRepository extends JpaRepository<ProgramTradingDailyHistory, Long> {

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    List<ProgramTradingDailyHistory> findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(
            String stockCode, LocalDate from, LocalDate to);
}
