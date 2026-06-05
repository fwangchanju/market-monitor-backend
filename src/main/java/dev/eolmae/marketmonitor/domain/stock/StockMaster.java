package dev.eolmae.marketmonitor.domain.stock;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "stock_master")
public class StockMaster {

	@Id
	@Column(length = 20)
	private String stockCode;

	@Column(nullable = false, length = 100)
	private String stockName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Exchange marketType;

	@Column(length = 5)
	private String marketCode;

	@Column
	private Long listCount;

	@Column(precision = 19, scale = 2)
	private BigDecimal lastPrice;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected StockMaster() {
	}

	public static StockMaster create(String stockCode, String stockName, Exchange marketType,
		String marketCode, Long listCount, BigDecimal lastPrice) {
		var entity = new StockMaster();
		entity.stockCode = stockCode;
		entity.stockName = stockName;
		entity.marketType = marketType;
		entity.marketCode = marketCode;
		entity.listCount = listCount;
		entity.lastPrice = lastPrice;
		entity.active = true;
		entity.createdAt = LocalDateTime.now();
		entity.updatedAt = LocalDateTime.now();
		return entity;
	}

	public void update(String stockName, Exchange marketType, String marketCode,
		Long listCount, BigDecimal lastPrice) {
		this.stockName = stockName;
		this.marketType = marketType;
		this.marketCode = marketCode;
		this.listCount = listCount;
		this.lastPrice = lastPrice;
		this.active = true;
		this.updatedAt = LocalDateTime.now();
	}

	public void markInactive() {
		this.active = false;
		this.updatedAt = LocalDateTime.now();
	}
}
