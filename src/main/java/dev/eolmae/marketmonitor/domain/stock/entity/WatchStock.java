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

    @Column(name = "stock_code", nullable = false, length = 20)
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

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected WatchStock() {}

    private static WatchStock create(String stockCode, RegisterBy registerBy, Integer holdingRank) {
        var entity = new WatchStock();
        entity.stockCode = stockCode;
        entity.isPrimary = false;
        entity.registerBy = registerBy;
        entity.holdingRank = holdingRank;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        entity.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
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
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    /** HOLDINGS였다면 USER로 바꿔 보유종목 동기화 삭제 대상에서 제외(대표 지정은 보유 여부와 무관하게 유지되어야 함) */
    public void designateAsPrimary() {
        this.registerBy = RegisterBy.USER;
        this.isPrimary = true;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void clearPrimary() {
        this.isPrimary = false;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public boolean isTopHoldingRank() {
        int topHoldingRank = 1;
        return Objects.equals(topHoldingRank, holdingRank);
    }
}
