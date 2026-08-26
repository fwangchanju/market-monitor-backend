package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQty;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockService;
import dev.eolmae.marketmonitor.domain.view.dto.*;
import dev.eolmae.marketmonitor.domain.view.enums.IntradayInvestorQuery;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.enums.RankingType;
import dev.eolmae.marketmonitor.domain.view.service.MarketQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api")
@RestController
@RequiredArgsConstructor
public class MarketController {

    private final MarketQueryService marketQueryService;
    private final WatchStockService watchStockService;

    // ── 메인 대시보드 ─────────────────────────────────────────────────────

    @GetMapping("/market-summary")
    public MarketSummaryResponse getMarketSummary() {
        return marketQueryService.getMarketSummary();
    }

    // ── 장중 투자자별 매매 상위 ────────────────────────────────────────────

    /**
     * 장중 투자자별 매매 상위 top 10.
     * market=COMBINED: KOSPI+KOSDAQ 합산, investor=FOREIGN_COMBINED: 외국인+외국계 합산.
     * ranking=NET_SELL: netBuyAmount 절댓값 변환 후 반환.
     */
    @GetMapping("/intraday-top")
    public SnapshotResponse<IntradayInvestorSummaryItem> getIntradayTop(
            @RequestParam MarketQuery market,
            @RequestParam IntradayInvestorQuery investor,
            @RequestParam RankingType ranking,
            @RequestParam AmtQty amtQty) {
        return marketQueryService.getIntradayTop(market, investor, ranking, amtQty);
    }

    // ── 프로그램매매 상위 상세 ────────────────────────────────────────────

    @GetMapping("/program-trading-rankings")
    public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            @RequestParam MarketQuery market, @RequestParam RankingType ranking, @RequestParam AmtQty amtQty) {
        return marketQueryService.getProgramTradingRankings(market, ranking, amtQty);
    }

    // ── 지수 기여도 상위 상세 ─────────────────────────────────────────────

    @GetMapping("/index-contribution")
    public SnapshotResponse<IndexContributionItem> getIndexContribution(@RequestParam Market market) {
        return marketQueryService.getIndexContribution(market);
    }

    // ── 종목 정보 ───────────────────────────────────────────────────────

    /** 활성 종목 전체 반환 — 관심종목 등록 화면 진입 시 1회 호출, 프론트 캐시 후 자동완성 */
    @GetMapping("/stocks")
    public List<StockResponse> getAllStocks() {
        return marketQueryService.getAllStocks();
    }

    // ── 관심종목 ─────────────────────────────────────────────────────────

    @GetMapping("/watch-stocks")
    public List<WatchStockResponse> getWatchStocks() {
        return marketQueryService.getWatchStocks();
    }

    @PostMapping("/watch-stocks/{stockCode}")
    public void registerWatchStock(@PathVariable String stockCode) {
        watchStockService.register(stockCode);
    }

    @DeleteMapping("/watch-stocks/{stockCode}")
    public void unregisterWatchStock(@PathVariable String stockCode) {
        watchStockService.unregister(stockCode);
    }

    @PatchMapping("/watch-stocks/{stockCode}/primary")
    public void designateAsPrimaryWatchStock(@PathVariable String stockCode) {
        watchStockService.designateAsPrimary(stockCode);
    }

    @PutMapping("/watch-stocks/{stockCode}/primary")
    public void registerAsPrimaryWatchStock(@PathVariable String stockCode) {
        watchStockService.registerAsPrimary(stockCode);
    }

    @DeleteMapping("/watch-stocks/{stockCode}/primary")
    public void clearPrimaryWatchStock(@PathVariable String stockCode) {
        watchStockService.clearPrimary(stockCode);
    }

    // ── 종목별 이력 ───────────────────────────────────────────────────────

    @GetMapping("/stocks/{stockCode}/program-trading")
    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistory(@PathVariable String stockCode) {
        return marketQueryService.getProgramTradingHistory(stockCode);
    }

    @GetMapping("/stocks/{stockCode}/program-trading/daily")
    public StockHistoryResponse<ProgramTradingDailyHistoryItem> getProgramTradingDailyHistory(
            @PathVariable String stockCode) {
        return marketQueryService.getProgramTradingDailyHistory(stockCode);
    }

    @GetMapping("/stocks/{stockCode}/short-selling")
    public StockHistoryResponse<ShortSellingHistoryItem> getShortSellingHistory(@PathVariable String stockCode) {
        return marketQueryService.getShortSellingHistory(stockCode);
    }
}
