package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(
        name = "program_trading_history",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_program_trading_history",
                        columnNames = {"stock_code", "snapshot_time"}))
@Entity
@Getter
public class ProgramTradingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programBuyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programSellAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programNetBuyAmount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ProgramTradingHistory() {}

    public static ProgramTradingHistory create(
            String stockCode,
            LocalDateTime snapshotTime,
            BigDecimal programBuyAmount,
            BigDecimal programSellAmount,
            BigDecimal programNetBuyAmount) {
        var entity = new ProgramTradingHistory();
        entity.stockCode = stockCode;
        entity.snapshotTime = snapshotTime;
        entity.programBuyAmount = programBuyAmount;
        entity.programSellAmount = programSellAmount;
        entity.programNetBuyAmount = programNetBuyAmount;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
