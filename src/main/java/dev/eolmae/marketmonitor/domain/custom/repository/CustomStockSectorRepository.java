package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSectorId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomStockSectorRepository extends JpaRepository<CustomStockSector, CustomStockSectorId> {

    @Query("select stockSector from CustomStockSector stockSector where stockSector.id.userId = :userId")
    List<CustomStockSector> findAllByUserId(@Param("userId") Long userId);

    @Query(
            "select stockSector from CustomStockSector stockSector where stockSector.id.userId = :userId and stockSector.id.stockCode = :stockCode")
    Optional<CustomStockSector> findByUserIdAndStockCode(
            @Param("userId") Long userId, @Param("stockCode") String stockCode);

    @Query(
            "select stockSector from CustomStockSector stockSector where stockSector.id.userId = :userId and stockSector.sectorId = :sectorId")
    List<CustomStockSector> findByUserIdAndSectorId(@Param("userId") Long userId, @Param("sectorId") Long sectorId);

    @Query(
            "select stockSector from CustomStockSector stockSector where stockSector.id.userId = :userId and stockSector.sectorId in :sectorIds")
    List<CustomStockSector> findByUserIdAndSectorIdIn(
            @Param("userId") Long userId, @Param("sectorIds") List<Long> sectorIds);

    @Query(
            "select stockSector from CustomStockSector stockSector where stockSector.id.userId = :userId and stockSector.id.stockCode in :stockCodes")
    List<CustomStockSector> findByUserIdAndStockCodeIn(
            @Param("userId") Long userId, @Param("stockCodes") List<String> stockCodes);

    @Modifying
    @Query(
            "delete from CustomStockSector stockSector where stockSector.id.userId = :userId and stockSector.id.stockCode = :stockCode")
    void deleteByIdUserIdAndIdStockCode(@Param("userId") Long userId, @Param("stockCode") String stockCode);

    @Modifying
    @Query(
            "delete from CustomStockSector stockSector where stockSector.id.userId = :userId and stockSector.sectorId in :sectorIds")
    void deleteByUserIdAndSectorIdIn(@Param("userId") Long userId, @Param("sectorIds") List<Long> sectorIds);

    @Modifying
    @Query("delete from CustomStockSector stockSector where stockSector.id.userId = :userId")
    void deleteAllByIdUserId(@Param("userId") Long userId);

    List<CustomStockSector> findBySectorId(Long sectorId);

    List<CustomStockSector> findBySectorIdIn(List<Long> sectorIds);

    void deleteBySectorIdIn(List<Long> sectorIds);
}
