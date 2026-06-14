package dev.eolmae.marketmonitor.domain.dashboard.controller;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.dashboard.dto.*;
import dev.eolmae.marketmonitor.domain.dashboard.service.DashboardQueryService;
import dev.eolmae.marketmonitor.domain.notification.service.DashboardSendService;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRankingType;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRankingType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService queryService;
    private final Optional<DashboardSendService> dashboardSendService;

    // ── 메인 대시보드 ─────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard() {
        return queryService.getDashboard();
    }

    @PostMapping("/send-dashboard") // renderer.enabled=true 시 활성화
    public SendDashboardResponse sendDashboard() {
        DashboardSendService svc =
                dashboardSendService.orElseThrow(() -> new BusinessException(ErrorCode.RENDERER_DISABLED));
        return new SendDashboardResponse(svc.sendDashboard());
    }

    // ── 장중 투자자별 매매 상위 ────────────────────────────────────────────

    /**
     * 장중 투자자별 매매 상위 top 10.
     * market=ALL: KOSPI+KOSDAQ 합산, investor=FOREIGN_TOTAL: 외국인+외국계 합산.
     * ranking=NET_SELL: netBuyAmount 절댓값 변환 후 반환.
     */
    @GetMapping("/intraday-top")
    public SnapshotResponse<IntradayInvestorSummaryItem> getIntradayTop(
            @RequestParam(required = false) Market market,
            @RequestParam IntradayInvestorType investor,
            @RequestParam IntradayRankingType ranking) {
        return queryService.getIntradayTop(market, investor, ranking);
    }

    /** 상세 랭킹 (기존 호환용) */
    @GetMapping("/intraday-rankings")
    public SnapshotResponse<IntradayInvestorRankingItem> getIntradayRankings(
            @RequestParam Market market,
            @RequestParam IntradayInvestorType investor,
            @RequestParam IntradayRankingType ranking) {
        return queryService.getIntradayRankings(market, investor, ranking);
    }

    // ── 프로그램매매 상위 상세 ────────────────────────────────────────────

    @GetMapping("/program-trading-rankings")
    public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            @RequestParam ProgramRankingType ranking,
            @RequestParam(required = false) Market market,
            @RequestParam(required = false) AmtQtyType amtQty) {
        AmtQtyType resolvedAmtQty = amtQty != null ? amtQty : AmtQtyType.AMOUNT;
        return queryService.getProgramTradingRankings(market, ranking, resolvedAmtQty);
    }

    // ── 지수 기여도 상위 상세 ─────────────────────────────────────────────

    @GetMapping("/index-contribution")
    public SnapshotResponse<IndexContributionItem> getIndexContribution(@RequestParam Market market) {
        return queryService.getIndexContribution(market);
    }

    // ── 종목 마스터 ───────────────────────────────────────────────────────

    /** 활성 종목 전체 반환 — 관심종목 등록 화면 진입 시 1회 호출, 프론트 캐시 후 자동완성 */
    @GetMapping("/stocks")
    public List<StockResponse> getAllStocks() {
        return queryService.getAllStocks();
    }

    // ── 관심종목 ─────────────────────────────────────────────────────────

    @GetMapping("/watch-stocks")
    public List<WatchStockResponse> getWatchStocks() {
        return queryService.getWatchStocks();
    }

    // ── 종목별 이력 ───────────────────────────────────────────────────────

    @GetMapping("/stocks/{stockCode}/program-trading")
    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistory(
            @PathVariable String stockCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return queryService.getProgramTradingHistory(stockCode, from, to);
    }

    // 종목별프로그램매매추이(일별)
    // @GetMapping("/stocks/{stockCode}/program-trading/daily")
    // public StockHistoryResponse<ProgramTradingDailyHistoryItem> getProgramTradingDailyHistory(
    //         @PathVariable String stockCode,
    //         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    //         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    //     return queryService.getProgramTradingDailyHistory(stockCode, from, to);
    // }

    @GetMapping("/stocks/{stockCode}/short-selling")
    public StockHistoryResponse<ShortSellingHistoryItem> getShortSellingHistory(@PathVariable String stockCode) {
        return queryService.getShortSellingHistory(stockCode);
    }
}
