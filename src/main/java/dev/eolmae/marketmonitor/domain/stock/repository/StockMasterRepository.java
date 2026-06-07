package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMasterRepository extends JpaRepository<StockMaster, String> {

    Optional<StockMaster> findByStockName(String stockName);

    List<StockMaster> findByActiveTrueOrderByStockCodeAsc();
}
