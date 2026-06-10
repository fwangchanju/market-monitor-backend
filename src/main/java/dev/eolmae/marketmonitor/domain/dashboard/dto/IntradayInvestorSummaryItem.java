package dev.eolmae.marketmonitor.domain.dashboard.dto;

import java.math.BigDecimal;

public record IntradayInvestorSummaryItem(String stockCode, String stockName, BigDecimal netBuyAmount) {}
