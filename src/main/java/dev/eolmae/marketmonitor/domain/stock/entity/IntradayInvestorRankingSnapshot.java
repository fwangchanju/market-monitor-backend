package dev.eolmae.marketmonitor.domain.stock.entity;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQty;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestor;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRanking;
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
        name = "intraday_investor_ranking_snapshot",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_intraday_investor_ranking_snapshot",
                        columnNames = {
                            "market_type",
                            "investor_type",
                            "ranking_type",
                            "amt_qty_type",
                            "stock_code",
                            "snapshot_time"
                        }))
@Entity
@Getter
public class IntradayInvestorRankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Market marketType;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IntradayInvestor investor;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IntradayRanking rankingType;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AmtQty amtQty;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    @Column(name = "rank_no", nullable = false)
    private int rank;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netBuyAmount;

    @Column(name = "sel_qty", nullable = false)
    private long sellVolume;

    @Column(nullable = false)
    private long tradedVolume;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected IntradayInvestorRankingSnapshot() {}

    public static IntradayInvestorRankingSnapshot create(
            Market marketType,
            IntradayInvestor investor,
            IntradayRanking rankingType,
            AmtQty amtQty,
            LocalDateTime snapshotTime,
            int rank,
            String stockCode,
            String stockName,
            BigDecimal netBuyAmount,
            long sellVolume,
            long tradedVolume) {
        var entity = new IntradayInvestorRankingSnapshot();
        entity.marketType = marketType;
        entity.investor = investor;
        entity.rankingType = rankingType;
        entity.amtQty = amtQty;
        entity.snapshotTime = snapshotTime;
        entity.rank = rank;
        entity.stockCode = stockCode;
        entity.stockName = stockName;
        entity.netBuyAmount = netBuyAmount;
        entity.sellVolume = sellVolume;
        entity.tradedVolume = tradedVolume;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
