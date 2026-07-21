package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortSellingDailyHistoryRepository
        extends JpaRepository<ShortSellingDailyHistory, Long>, ShortSellingDailyHistoryRepositoryCustom {

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);
}
