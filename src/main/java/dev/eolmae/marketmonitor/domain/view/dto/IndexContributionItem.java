package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;

public record IndexContributionItem(
        Market marketType,
        int rank,
        String stockCode,
        String stockName,
        BigDecimal contributionScore,
        BigDecimal priceChangeRate) {}
