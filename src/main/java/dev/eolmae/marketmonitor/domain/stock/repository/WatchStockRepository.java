package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.*;
import dev.eolmae.marketmonitor.domain.stock.enums.RegisterBy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WatchStockRepository extends JpaRepository<WatchStock, Long> {

    List<WatchStock> findByRegisterBy(RegisterBy registerBy);

    @Query("SELECT DISTINCT ws.stock.stockCode FROM WatchStock ws")
    List<String> findDistinctStockCodes();

    boolean existsByStockStockCode(String stockCode);

    List<WatchStock> findByIsPrimaryTrue();
}
