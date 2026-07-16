package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.enums.RegisterBy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchStockRepository extends JpaRepository<WatchStock, Long> {

    void deleteByRegisterBy(RegisterBy registerBy);

    void deleteByRegisterByAndStockCodeNotIn(RegisterBy registerBy, Collection<String> stockCodes);

    List<WatchStock> findByRegisterBy(RegisterBy registerBy);

    Optional<WatchStock> findByStockCode(String stockCode);
}
