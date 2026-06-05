package dev.eolmae.marketmonitor.domain.dashboard;

import dev.eolmae.marketmonitor.common.enums.AmtQtyType;
import dev.eolmae.marketmonitor.common.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.common.enums.IntradayRankingType;
import dev.eolmae.marketmonitor.common.enums.Exchange;
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
	name = "intraday_investor_ranking_snapshot",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_intraday_investor_ranking_snapshot",
		columnNames = {"market_type", "investor_type", "ranking_type", "amt_qty_type", "stock_code", "snapshot_time"}
	)
)
public class IntradayInvestorRankingSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Exchange marketType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IntradayInvestorType investorType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IntradayRankingType rankingType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AmtQtyType amtQtyType;

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
	private LocalDateTime snapshotTime;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected IntradayInvestorRankingSnapshot() {
	}

	public static IntradayInvestorRankingSnapshot create(
		Exchange marketType, IntradayInvestorType investorType,
		IntradayRankingType rankingType, AmtQtyType amtQtyType,
		int rank, String stockCode, String stockName,
		BigDecimal netBuyAmount, long sellVolume, long tradedVolume,
		LocalDateTime snapshotTime) {
		var entity = new IntradayInvestorRankingSnapshot();
		entity.marketType = marketType;
		entity.investorType = investorType;
		entity.rankingType = rankingType;
		entity.amtQtyType = amtQtyType;
		entity.rank = rank;
		entity.stockCode = stockCode;
		entity.stockName = stockName;
		entity.netBuyAmount = netBuyAmount;
		entity.sellVolume = sellVolume;
		entity.tradedVolume = tradedVolume;
		entity.snapshotTime = snapshotTime;
		entity.createdAt = LocalDateTime.now();
		return entity;
	}
}
