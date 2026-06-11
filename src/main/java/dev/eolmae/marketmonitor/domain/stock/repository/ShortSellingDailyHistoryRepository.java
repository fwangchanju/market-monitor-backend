package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortSellingDailyHistoryRepository extends JpaRepository<ShortSellingDailyHistory, Long> {

    boolean existsByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    List<ShortSellingDailyHistory> findByStockCodeOrderByTradeDateDesc(String stockCode);
}
