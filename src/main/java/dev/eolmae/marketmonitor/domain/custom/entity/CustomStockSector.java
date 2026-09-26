package dev.eolmae.marketmonitor.domain.custom.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(name = "custom_stock_sector")
@Entity
@Getter
public class CustomStockSector {

    @EmbeddedId
    private CustomStockSectorId id;

    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CustomStockSector() {}

    public static CustomStockSector create(Long userId, String stockCode, Long sectorId) {
        var entity = new CustomStockSector();
        entity.id = new CustomStockSectorId(userId, stockCode);
        entity.sectorId = sectorId;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public String getStockCode() {
        return id.getStockCode();
    }

    public void reassign(Long sectorId) {
        this.sectorId = sectorId;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
