package dev.eolmae.marketmonitor.domain.marketmapcategory.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "market_map_stock_category")
public class MarketMapStockCategory {

    @Id
    @Column(name = "stock_code", length = 20)
    private String stockCode;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MarketMapStockCategory() {}

    public static MarketMapStockCategory create(String stockCode, Long categoryId) {
        var entity = new MarketMapStockCategory();
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
}
