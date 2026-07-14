package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.view.dto.*;
import dev.eolmae.marketmonitor.domain.view.enums.IntradayInvestorQuery;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.enums.RankingType;
import dev.eolmae.marketmonitor.domain.view.service.MarketQueryService;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.service.StockCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MarketController {

    private final MarketQueryService marketQueryService;
    private final StockCategoryService stockCategoryService;

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
            @RequestParam AmtQtyType amtQty) {
        return marketQueryService.getIntradayTop(market, investor, ranking, amtQty);
    }

    /** 상세 랭킹 (기존 호환용) */
    //    @GetMapping("/intraday-rankings")
    //    public SnapshotResponse<IntradayInvestorRankingItem> getIntradayRankings(
    //            @RequestParam Market market,
    //            @RequestParam IntradayInvestorType investor,
    //            @RequestParam IntradayRankingType ranking) {
    //        return marketQueryService.getIntradayRankings(market, investor, ranking);
    //    }
    // TODO 화면에서 미사용 확인됨 — 화면 점검 후 이상 없으면 서비스 레이어 메서드까지 삭제

    // ── 프로그램매매 상위 상세 ────────────────────────────────────────────

    @GetMapping("/program-trading-rankings")
    public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            @RequestParam MarketQuery market,
            @RequestParam RankingType ranking,
            @RequestParam AmtQtyType amtQty) {
        return marketQueryService.getProgramTradingRankings(market, ranking, amtQty);
    }

    // ── 지수 기여도 상위 상세 ─────────────────────────────────────────────

    @GetMapping("/index-contribution")
    public SnapshotResponse<IndexContributionItem> getIndexContribution(@RequestParam Market market) {
        return marketQueryService.getIndexContribution(market);
    }

    // ── 종목 마스터 ───────────────────────────────────────────────────────

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

    // ── 종목별 이력 ───────────────────────────────────────────────────────

    @GetMapping("/stocks/{stockCode}/program-trading")
    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistory(
            @PathVariable String stockCode) {
        return marketQueryService.getProgramTradingHistory(stockCode);
    }

    @GetMapping("/stocks/{stockCode}/program-trading/range")
    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistoryByRange(
            @PathVariable String stockCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return marketQueryService.getProgramTradingHistoryByRange(stockCode, from, to);
    }

    @GetMapping("/stocks/{stockCode}/short-selling")
    public StockHistoryResponse<ShortSellingHistoryItem> getShortSellingHistory(@PathVariable String stockCode) {
        return marketQueryService.getShortSellingHistory(stockCode);
    }

    @PatchMapping("/stocks/{stockCode}/category")
    public void reassignCategory(@PathVariable String stockCode, @RequestBody StockCategoryRequest request) {
        stockCategoryService.reassign(stockCode, request.categoryName());
    }
}
