package dev.eolmae.marketmonitor.domain.dashboard.dto;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import java.math.BigDecimal;

public record IntradayInvestorRankingItem(
        Exchange marketType,
        IntradayInvestorType investorType,
        int rank,
        String stockCode,
        String stockName,
        BigDecimal netBuyAmount,
        long tradedVolume) {}
