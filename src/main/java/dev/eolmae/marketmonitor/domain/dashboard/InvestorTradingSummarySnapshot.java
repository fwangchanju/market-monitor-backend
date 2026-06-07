package dev.eolmae.marketmonitor.domain.dashboard;

import dev.eolmae.marketmonitor.common.enums.AmtQtyType;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.InvestorType;
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

@Getter
@Entity
@Table(
        name = "investor_trading_summary_snapshot",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_investor_trading_summary_snapshot",
                        columnNames = {"market_type", "investor_type", "amt_qty_type", "snapshot_time"}))
public class InvestorTradingSummarySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Exchange marketType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvestorType investorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AmtQtyType amtQtyType;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    @Column(nullable = false)
    private LocalDateTime lastCollectedAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal buyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal sellAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netBuyAmount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected InvestorTradingSummarySnapshot() {}

    public static InvestorTradingSummarySnapshot create(
            Exchange marketType,
            InvestorType investorType,
            AmtQtyType amtQtyType,
            LocalDateTime snapshotTime,
            LocalDateTime lastCollectedAt,
            BigDecimal buyAmount,
            BigDecimal sellAmount,
            BigDecimal netBuyAmount) {
        var entity = new InvestorTradingSummarySnapshot();
        entity.marketType = marketType;
        entity.investorType = investorType;
        entity.amtQtyType = amtQtyType;
        entity.snapshotTime = snapshotTime;
        entity.lastCollectedAt = lastCollectedAt;
        entity.buyAmount = buyAmount;
        entity.sellAmount = sellAmount;
        entity.netBuyAmount = netBuyAmount;
        entity.createdAt = LocalDateTime.now();
        return entity;
    }
}
