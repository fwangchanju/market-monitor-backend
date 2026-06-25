package dev.eolmae.marketmonitor.domain.view.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MarketSummaryResponse(
        LocalDateTime snapshotTime,
        LocalDateTime lastCollectedAt,
        String marketStatus,
        List<MarketOverviewItem> marketOverviews,
        List<InvestorTradingSummaryItem> investorTradingSummaries,
        List<IntradayInvestorRankingItem> intradayTopRankings,
        List<ProgramTradingRankingItem> programTradingHighlights,
        List<IndexContributionItem> indexContributionHighlights,
        List<WatchStockResponse> watchStocks) {}
