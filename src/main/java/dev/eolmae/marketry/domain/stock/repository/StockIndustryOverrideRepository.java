package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.StockIndustryOverride;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockIndustryOverrideRepository extends JpaRepository<StockIndustryOverride, String> {}
