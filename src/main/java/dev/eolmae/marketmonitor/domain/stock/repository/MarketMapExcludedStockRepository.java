package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.MarketMapExcludedStock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMapExcludedStockRepository extends JpaRepository<MarketMapExcludedStock, String> {

    List<MarketMapExcludedStock> findByIsActiveTrue();
}
