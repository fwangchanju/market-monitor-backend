package dev.eolmae.marketmonitor.domain.view.dto;

public record MarketSummaryResponse(
        SnapshotResponse<MarketOverviewItem> marketOverviews,
        SnapshotResponse<InvestorTradingSummaryItem> investorTradingSummaries,
        SnapshotResponse<IntradayInvestorRankingItem> intradayTopRankings,
        SnapshotResponse<ProgramTradingRankingItem> programTradingHighlights,
        SnapshotResponse<IndexContributionItem> indexContributionHighlights) {}
