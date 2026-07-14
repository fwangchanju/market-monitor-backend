package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "market_map_excluded_stock")
public class MarketMapExcludedStock {

    @Id
    @Column(length = 20)
    private String stockCode;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MarketMapExcludedStock() {}

    public static MarketMapExcludedStock create(String stockCode) {
        var entity = new MarketMapExcludedStock();
        entity.stockCode = stockCode;
        entity.isActive = true;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        entity.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }

    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
