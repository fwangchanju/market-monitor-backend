package dev.eolmae.marketmonitor.domain.marketmap.repository;

import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMapStockCategoryRepository extends JpaRepository<MarketMapStockCategory, String> {

    List<MarketMapStockCategory> findByCategoryId(Long categoryId);

    List<MarketMapStockCategory> findByCategoryIdIn(List<Long> categoryIds);
}
