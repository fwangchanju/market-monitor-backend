package dev.eolmae.marketry.domain.view.dto;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.enums.Investor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvestorTradingSummaryItem(
        Market market,
        Investor investor,
        BigDecimal buyAmount,
        BigDecimal sellAmount,
        BigDecimal netBuyAmount,
        LocalDateTime snapshotTime) {}
