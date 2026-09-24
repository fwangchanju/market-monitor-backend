package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomStockSectorRepository extends JpaRepository<CustomStockSector, String> {

    List<CustomStockSector> findBySectorId(Long sectorId);

    List<CustomStockSector> findBySectorIdIn(List<Long> sectorIds);

    void deleteBySectorIdIn(List<Long> sectorIds);
}
