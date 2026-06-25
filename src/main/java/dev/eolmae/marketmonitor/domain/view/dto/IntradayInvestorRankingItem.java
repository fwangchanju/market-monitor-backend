package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import java.math.BigDecimal;

public record IntradayInvestorRankingItem(
        Market marketType,
        IntradayInvestorType investorType,
        int rank,
        String stockCode,
        String stockName,
        BigDecimal netBuyAmount,
        long tradedVolume) {}
