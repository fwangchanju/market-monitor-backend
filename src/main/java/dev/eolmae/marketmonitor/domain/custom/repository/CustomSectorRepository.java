package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomSectorRepository extends JpaRepository<CustomSector, Long> {

    List<CustomSector> findByParentId(Long parentId);

    Optional<CustomSector> findFirstByOrderByIdAsc();

    boolean existsByName(String name);
}
