package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.enums.InvestorType;
import java.math.BigDecimal;

public record InvestorTradingSummaryItem(
        Market marketType,
        InvestorType investorType,
        BigDecimal buyAmount,
        BigDecimal sellAmount,
        BigDecimal netBuyAmount) {}
