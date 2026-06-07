package dev.eolmae.marketmonitor.api.dto;

import java.math.BigDecimal;

public record IntradayInvestorTopItem(String stockCode, String stockName, BigDecimal netBuyAmount) {}
