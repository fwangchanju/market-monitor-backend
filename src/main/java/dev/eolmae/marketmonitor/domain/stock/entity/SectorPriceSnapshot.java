package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.enums.ExchangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(
        name = "sector_price_snapshot",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_sector_price_snapshot",
                        columnNames = {"market_type", "stock_code", "exchange_type", "snapshot_time"}))
@Entity
@Getter
public class SectorPriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Market marketType;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ExchangeType exchangeType;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal changeValue;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal changeRate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SectorPriceSnapshot() {}

    public static SectorPriceSnapshot create(
            Market marketType,
            LocalDateTime snapshotTime,
            String stockCode,
            ExchangeType exchangeType,
            String stockName,
            BigDecimal currentPrice,
            BigDecimal changeValue,
            BigDecimal changeRate) {
        var entity = new SectorPriceSnapshot();
        entity.marketType = marketType;
        entity.stockCode = stockCode;
        entity.exchangeType = exchangeType;
        entity.snapshotTime = snapshotTime;
        entity.stockName = stockName;
        entity.currentPrice = currentPrice;
        entity.changeValue = changeValue;
        entity.changeRate = changeRate;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
