package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockInfoRepository extends JpaRepository<StockInfo, String> {

    Optional<StockInfo> findByStockName(String stockName);

    List<StockInfo> findByActiveTrueOrderByStockCodeAsc();
}
