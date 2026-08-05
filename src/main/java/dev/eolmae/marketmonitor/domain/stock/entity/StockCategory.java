package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(name = "stock_category")
@Entity
@Getter
public class StockCategory {

    @Id
    @Column(length = 20)
    private String stockCode;

    @Column(nullable = false, length = 50)
    private String categoryName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StockCategory() {}

    public static StockCategory create(String stockCode, String categoryName) {
        var entity = new StockCategory();
        entity.stockCode = stockCode;
        entity.categoryName = categoryName;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        entity.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }

    public void update(String categoryName) {
        this.categoryName = categoryName;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
