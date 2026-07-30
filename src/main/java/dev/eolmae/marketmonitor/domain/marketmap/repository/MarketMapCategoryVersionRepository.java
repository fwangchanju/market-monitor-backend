package dev.eolmae.marketmonitor.domain.marketmap.repository;

import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategoryVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMapCategoryVersionRepository extends JpaRepository<MarketMapCategoryVersion, Long> {

    List<MarketMapCategoryVersion> findAllByOrderByCreatedAtDesc();
}
