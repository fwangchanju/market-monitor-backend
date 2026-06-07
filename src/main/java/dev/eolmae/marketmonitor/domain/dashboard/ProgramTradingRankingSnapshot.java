package dev.eolmae.marketmonitor.domain.dashboard;

import dev.eolmae.marketmonitor.common.enums.AmtQtyType;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.ProgramRankingType;
import dev.eolmae.marketmonitor.common.enums.Zone;
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
        name = "program_trading_ranking_snapshot",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_program_trading_ranking_snapshot",
                        columnNames = {"market_type", "amt_qty_type", "ranking_type", "stock_code", "snapshot_time"}))
public class ProgramTradingRankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Exchange marketType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AmtQtyType amtQtyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgramRankingType rankingType;

    @Column(name = "rank_no", nullable = false)
    private int rank;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programBuyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programSellAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal programNetBuyAmount;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ProgramTradingRankingSnapshot() {}

    public static ProgramTradingRankingSnapshot create(
            Exchange marketType,
            AmtQtyType amtQtyType,
            ProgramRankingType rankingType,
            int rank,
            String stockCode,
            String stockName,
            BigDecimal programBuyAmount,
            BigDecimal programSellAmount,
            BigDecimal programNetBuyAmount,
            LocalDateTime snapshotTime) {
        var entity = new ProgramTradingRankingSnapshot();
        entity.marketType = marketType;
        entity.amtQtyType = amtQtyType;
        entity.rankingType = rankingType;
        entity.rank = rank;
        entity.stockCode = stockCode;
        entity.stockName = stockName;
        entity.programBuyAmount = programBuyAmount;
        entity.programSellAmount = programSellAmount;
        entity.programNetBuyAmount = programNetBuyAmount;
        entity.snapshotTime = snapshotTime;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
