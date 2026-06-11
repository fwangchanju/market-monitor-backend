package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.enums.RegisterBy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "watch_stock")
public class WatchStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stock;

    @Column(nullable = false)
    private boolean isPrimary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegisterBy registerBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WatchStock() {}

    public static WatchStock create(StockInfo stock, RegisterBy registerBy) {
        var entity = new WatchStock();
        entity.stock = stock;
        entity.isPrimary = false;
        entity.registerBy = registerBy;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
