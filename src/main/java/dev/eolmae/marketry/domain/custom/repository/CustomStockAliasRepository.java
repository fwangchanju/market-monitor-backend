package dev.eolmae.marketry.domain.custom.repository;

import dev.eolmae.marketry.domain.custom.entity.CustomStockAlias;
import dev.eolmae.marketry.domain.custom.entity.CustomStockAliasId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomStockAliasRepository extends JpaRepository<CustomStockAlias, CustomStockAliasId> {

    List<CustomStockAlias> findAllByIdUserId(Long userId);
}
