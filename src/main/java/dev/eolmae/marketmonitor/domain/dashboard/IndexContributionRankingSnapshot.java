package dev.eolmae.marketmonitor.domain.dashboard;

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
	name = "index_contribution_ranking_snapshot",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_index_contribution_ranking_snapshot",
		columnNames = {"market_type", "stock_code", "snapshot_time"}
	)
)
public class IndexContributionRankingSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Exchange marketType;

	@Column(name = "rank_no", nullable = false)
	private int rank;

	@Column(nullable = false, length = 20)
	private String stockCode;

	@Column(nullable = false, length = 100)
	private String stockName;

	@Column(length = 5)
	private String marketCode;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal contributionScore;

	@Column(nullable = false, precision = 9, scale = 4)
	private BigDecimal priceChangeRate;

	@Column(nullable = false)
	private LocalDateTime snapshotTime;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected IndexContributionRankingSnapshot() {
	}

	public static IndexContributionRankingSnapshot create(Exchange marketType, int rank,
		String stockCode, String stockName, String marketCode,
		BigDecimal contributionScore, BigDecimal priceChangeRate,
		LocalDateTime snapshotTime) {
		var entity = new IndexContributionRankingSnapshot();
		entity.marketType = marketType;
		entity.rank = rank;
		entity.stockCode = stockCode;
		entity.stockName = stockName;
		entity.marketCode = marketCode;
		entity.contributionScore = contributionScore;
		entity.priceChangeRate = priceChangeRate;
		entity.snapshotTime = snapshotTime;
		entity.createdAt = LocalDateTime.now();
		return entity;
	}
}
