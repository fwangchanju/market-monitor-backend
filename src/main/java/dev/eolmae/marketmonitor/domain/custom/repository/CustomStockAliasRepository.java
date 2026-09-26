package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockAlias;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockAliasId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomStockAliasRepository extends JpaRepository<CustomStockAlias, CustomStockAliasId> {

    List<CustomStockAlias> findAllByIdUserId(Long userId);
}
