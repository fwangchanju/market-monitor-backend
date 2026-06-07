package dev.eolmae.marketmonitor.api.dto;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.InvestorType;
import java.math.BigDecimal;

public record InvestorTradingSummaryItem(
        Exchange marketType,
        InvestorType investorType,
        BigDecimal buyAmount,
        BigDecimal sellAmount,
        BigDecimal netBuyAmount) {}
