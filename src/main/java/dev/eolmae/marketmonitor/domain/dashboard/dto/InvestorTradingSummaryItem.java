package dev.eolmae.marketmonitor.domain.dashboard.dto;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.domain.stock.enums.InvestorType;
import java.math.BigDecimal;

public record InvestorTradingSummaryItem(
        Exchange marketType,
        InvestorType investorType,
        BigDecimal buyAmount,
        BigDecimal sellAmount,
        BigDecimal netBuyAmount) {}
