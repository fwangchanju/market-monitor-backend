package dev.eolmae.marketmonitor.domain.view.dto;

public record MarketSummaryResponse(
        SnapshotResponse<MarketOverviewItem> marketOverviews,
        SnapshotResponse<InvestorTradingSummaryItem> investorTradingSummaries,
        SnapshotResponse<IntradayInvestorSummaryItem> intradayTopRankings,
        SnapshotResponse<ProgramTradingRankingItem> programTradingHighlights,
        SnapshotResponse<IndexContributionItem> indexContributionHighlights,
        StockHistoryResponse<ShortSellingHistoryItem> mainShortSellingHistory,
        StockHistoryResponse<ProgramTradingHistoryItem> mainProgramTradingHistory) {}
