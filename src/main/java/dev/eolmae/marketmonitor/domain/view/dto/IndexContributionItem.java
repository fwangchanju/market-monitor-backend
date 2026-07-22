package dev.eolmae.marketmonitor.domain.view.dto;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IndexContributionItem(
        Market market,
        int rank,
        String stockCode,
        String stockName,
        BigDecimal contributionScore,
        BigDecimal priceChangeRate,
        LocalDateTime snapshotTime) {}
