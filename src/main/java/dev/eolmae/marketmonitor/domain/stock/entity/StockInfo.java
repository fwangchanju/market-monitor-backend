package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "stock_info")
public class StockInfo {

    @Id
    @Column(length = 20)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market marketType;

    @Column(length = 5)
    private String marketCode;

    @Column(length = 50)
    private String categoryName;

    @Column
    private Long listCount;

    @Column(precision = 19, scale = 2)
    private BigDecimal lastPrice;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StockInfo() {}

    public static StockInfo create(
            String stockCode,
            String stockName,
            Market marketType,
            String marketCode,
            String categoryName,
            Long listCount,
            BigDecimal lastPrice) {
        var entity = new StockInfo();
        entity.stockCode = stockCode;
        entity.stockName = stockName;
        entity.marketType = marketType;
        entity.marketCode = marketCode;
        entity.categoryName = categoryName;
        entity.listCount = listCount;
        entity.lastPrice = lastPrice;
        entity.active = true;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        entity.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }

    public void update(
            String stockName, Market marketType, String marketCode, String categoryName, Long listCount, BigDecimal lastPrice) {
        this.stockName = stockName;
        this.marketType = marketType;
        this.marketCode = marketCode;
        this.categoryName = categoryName;
        this.listCount = listCount;
        this.lastPrice = lastPrice;
        this.active = true;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void markInactive() {
        this.active = false;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
