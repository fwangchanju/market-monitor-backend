package dev.eolmae.marketmonitor.domain.custom.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(name = "custom_stock_sector")
@Entity
@Getter
public class CustomStockSector {

    @Id
    @Column(name = "stock_code", length = 20)
    private String stockCode;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(length = 50)
    private String alias;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CustomStockSector() {}

    public static CustomStockSector create(String stockCode, Long categoryId) {
        var entity = new CustomStockSector();
        entity.stockCode = stockCode;
        entity.categoryId = categoryId;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void reassign(Long categoryId) {
        this.categoryId = categoryId;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void updateAlias(String alias) {
        this.alias = alias;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
