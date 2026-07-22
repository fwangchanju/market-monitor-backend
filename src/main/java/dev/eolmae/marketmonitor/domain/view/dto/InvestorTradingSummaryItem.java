package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.enums.Investor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvestorTradingSummaryItem(
        Market market,
        Investor investor,
        BigDecimal buyAmount,
        BigDecimal sellAmount,
        BigDecimal netBuyAmount,
        LocalDateTime snapshotTime) {}
