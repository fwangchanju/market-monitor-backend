package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomSectorRepository extends JpaRepository<CustomSector, Long> {

    List<CustomSector> findAllByUserId(Long userId);

    List<CustomSector> findByUserIdAndParentId(Long userId, Long parentId);

    Optional<CustomSector> findFirstByUserIdOrderByIdAsc(Long userId);

    Optional<CustomSector> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    void deleteAllByUserId(Long userId);
}
