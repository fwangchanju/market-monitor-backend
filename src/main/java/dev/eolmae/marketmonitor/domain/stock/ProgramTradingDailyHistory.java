package dev.eolmae.marketmonitor.domain.stock;

import dev.eolmae.marketmonitor.common.enums.Zone;
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
        name = "program_trading_daily",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_program_trading_daily",
                        columnNames = {"stock_code", "trade_date"}))
public class ProgramTradingDailyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programBuyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programSellAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programNetBuyAmount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ProgramTradingDailyHistory() {}

    public static ProgramTradingDailyHistory create(
            String stockCode,
            LocalDate tradeDate,
            BigDecimal programBuyAmount,
            BigDecimal programSellAmount,
            BigDecimal programNetBuyAmount) {
        var entity = new ProgramTradingDailyHistory();
        entity.stockCode = stockCode;
        entity.tradeDate = tradeDate;
        entity.programBuyAmount = programBuyAmount;
        entity.programSellAmount = programSellAmount;
        entity.programNetBuyAmount = programNetBuyAmount;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
