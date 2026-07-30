package dev.eolmae.marketmonitor.domain.marketmapcategory.repository;

import dev.eolmae.marketmonitor.domain.marketmapcategory.entity.MarketMapStockCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMapStockCategoryRepository extends JpaRepository<MarketMapStockCategory, String> {

    List<MarketMapStockCategory> findByCategoryId(Long categoryId);
}
