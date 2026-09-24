package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomStockSectorRepository extends JpaRepository<CustomStockSector, String> {

    List<CustomStockSector> findByCategoryId(Long categoryId);

    List<CustomStockSector> findByCategoryIdIn(List<Long> categoryIds);

    void deleteByCategoryIdIn(List<Long> categoryIds);
}
