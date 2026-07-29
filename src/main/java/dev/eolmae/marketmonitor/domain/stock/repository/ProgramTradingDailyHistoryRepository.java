package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramTradingDailyHistoryRepository
        extends JpaRepository<ProgramTradingDailyHistory, Long>, ProgramTradingDailyHistoryRepositoryCustom {

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);
}
