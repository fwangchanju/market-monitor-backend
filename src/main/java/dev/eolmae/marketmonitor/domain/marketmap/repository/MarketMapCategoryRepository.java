package dev.eolmae.marketmonitor.domain.marketmap.repository;

import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMapCategoryRepository extends JpaRepository<MarketMapCategory, Long> {

    List<MarketMapCategory> findByParentId(Long parentId);

    Optional<MarketMapCategory> findFirstByOrderByIdAsc();

    boolean existsByName(String name);
}
