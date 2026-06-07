package dev.eolmae.marketmonitor.domain.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "short_selling_daily",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_short_selling_daily",
                        columnNames = {"stock_code", "trade_date"}))
public class ShortSellingDailyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closePrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal priceChange;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal changeRate;

    @Column(nullable = false)
    private long tradingVolume;

    @Column(nullable = false)
    private long shortVolume;

    @Column(nullable = false)
    private long cumulativeShortVolume;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal shortRatio;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal shortAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal shortAvgPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ShortSellingDailyHistory() {}

    public static ShortSellingDailyHistory create(
            String stockCode,
            LocalDate tradeDate,
            BigDecimal closePrice,
            BigDecimal priceChange,
            BigDecimal changeRate,
            long tradingVolume,
            long shortVolume,
            long cumulativeShortVolume,
            BigDecimal shortRatio,
            BigDecimal shortAmount,
            BigDecimal shortAvgPrice) {
        var entity = new ShortSellingDailyHistory();
        entity.stockCode = stockCode;
        entity.tradeDate = tradeDate;
        entity.closePrice = closePrice;
        entity.priceChange = priceChange;
        entity.changeRate = changeRate;
        entity.tradingVolume = tradingVolume;
        entity.shortVolume = shortVolume;
        entity.cumulativeShortVolume = cumulativeShortVolume;
        entity.shortRatio = shortRatio;
        entity.shortAmount = shortAmount;
        entity.shortAvgPrice = shortAvgPrice;
        entity.createdAt = LocalDateTime.now();
        return entity;
    }
}
