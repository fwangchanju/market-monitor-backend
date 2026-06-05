package dev.eolmae.marketmonitor.api.dto;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import java.math.BigDecimal;

public record IndexContributionItem(
	Exchange marketType,
	int rank,
	String stockCode,
	String stockName,
	BigDecimal contributionScore,
	BigDecimal priceChangeRate
) {
}
