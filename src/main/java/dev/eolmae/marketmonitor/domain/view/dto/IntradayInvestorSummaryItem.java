package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IntradayInvestorSummaryItem(
        String stockCode, String stockName, BigDecimal netBuyAmount, LocalDateTime snapshotTime) {}
