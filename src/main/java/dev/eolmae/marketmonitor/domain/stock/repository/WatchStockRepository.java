package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.enums.RegisterBy;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WatchStockRepository extends JpaRepository<WatchStock, Long> {

    @Modifying
    @Query("DELETE FROM WatchStock ws WHERE ws.registerBy = :registerBy")
    void deleteByRegisterBy(RegisterBy registerBy);

    @Modifying
    @Query("DELETE FROM WatchStock ws WHERE ws.registerBy = :registerBy AND ws.stockCode NOT IN :stockCodes")
    void deleteByRegisterByAndStockCodeNotIn(RegisterBy registerBy, Collection<String> stockCodes);

    List<WatchStock> findByRegisterBy(RegisterBy registerBy);
}
