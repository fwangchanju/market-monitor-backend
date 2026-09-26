package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSectorId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomStockSectorRepository
        extends JpaRepository<CustomStockSector, CustomStockSectorId>, CustomStockSectorRepositoryCustom {

    List<CustomStockSector> findAllByIdUserId(Long userId);

    List<CustomStockSector> findByIdUserIdAndSectorId(Long userId, Long sectorId);

    List<CustomStockSector> findByIdUserIdAndSectorIdIn(Long userId, List<Long> sectorIds);

    List<CustomStockSector> findByIdUserIdAndIdStockCodeIn(Long userId, List<String> stockCodes);
}
