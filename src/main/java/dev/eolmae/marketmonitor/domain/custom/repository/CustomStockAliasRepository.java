package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockAlias;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockAliasId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomStockAliasRepository extends JpaRepository<CustomStockAlias, CustomStockAliasId> {

    @Query("select alias from CustomStockAlias alias where alias.id.userId = :userId")
    List<CustomStockAlias> findAllByIdUserId(@Param("userId") Long userId);
}
