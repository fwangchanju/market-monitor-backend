package dev.eolmae.marketmonitor.api.dto;

import dev.eolmae.marketmonitor.common.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import java.math.BigDecimal;

public record IntradayInvestorRankingItem(
	Exchange marketType,
	IntradayInvestorType investorType,
	int rank,
	String stockCode,
	String stockName,
	BigDecimal netBuyAmount,
	long tradedVolume
) {
}
