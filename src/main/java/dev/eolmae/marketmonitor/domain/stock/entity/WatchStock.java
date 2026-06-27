package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.enums.RegisterBy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(name = "watch_stock")
public class WatchStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(nullable = false)
    private boolean isPrimary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegisterBy registerBy;

    @Column(name = "holding_rank")
    private Integer holdingRank;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WatchStock() {}

    private static WatchStock create(String stockCode, RegisterBy registerBy, Integer holdingRank) {
        var entity = new WatchStock();
        entity.stockCode = stockCode;
        entity.isPrimary = false;
        entity.registerBy = registerBy;
        entity.holdingRank = holdingRank;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }

    public static WatchStock createManual(String stockCode) {
        return create(stockCode, RegisterBy.USER, null);
    }

    public static WatchStock createHolding(String stockCode, int holdingRank) {
        return create(stockCode, RegisterBy.HOLDINGS, holdingRank);
    }

    public void updateHoldingRank(Integer holdingRank) {
        this.holdingRank = holdingRank;
    }

    public boolean isTopHoldingRank() {
        int topHoldingRank = 1;
        return Objects.equals(topHoldingRank, holdingRank);
    }
}
