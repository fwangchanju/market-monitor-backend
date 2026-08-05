package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQty;
import dev.eolmae.marketmonitor.domain.stock.enums.Investor;
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
        name = "investor_trading_summary_snapshot",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_investor_trading_summary_snapshot",
                        columnNames = {"market_type", "investor_type", "amt_qty_type", "snapshot_time"}))
@Entity
@Getter
public class InvestorTradingSummarySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Market marketType;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Investor investor;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AmtQty amtQty;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal buyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal sellAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netBuyAmount;

    @Column(nullable = false)
    private LocalDateTime lastCollectedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected InvestorTradingSummarySnapshot() {}

    public static InvestorTradingSummarySnapshot create(
            Market marketType,
            Investor investor,
            AmtQty amtQty,
            LocalDateTime snapshotTime,
            BigDecimal buyAmount,
            BigDecimal sellAmount,
            BigDecimal netBuyAmount,
            LocalDateTime lastCollectedAt) {
        var entity = new InvestorTradingSummarySnapshot();
        entity.marketType = marketType;
        entity.investor = investor;
        entity.amtQty = amtQty;
        entity.snapshotTime = snapshotTime;
        entity.buyAmount = buyAmount;
        entity.sellAmount = sellAmount;
        entity.netBuyAmount = netBuyAmount;
        entity.lastCollectedAt = lastCollectedAt;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
