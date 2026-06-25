package dev.eolmae.marketmonitor.domain.view.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.view.dto.MarketSummaryResponse;
import dev.eolmae.marketmonitor.domain.view.dto.IndexContributionItem;
import dev.eolmae.marketmonitor.domain.view.dto.IntradayInvestorRankingItem;
import dev.eolmae.marketmonitor.domain.view.dto.IntradayInvestorSummaryItem;
import dev.eolmae.marketmonitor.domain.view.dto.InvestorTradingSummaryItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketOverviewItem;
import dev.eolmae.marketmonitor.domain.view.dto.ProgramTradingDailyHistoryItem;
import dev.eolmae.marketmonitor.domain.view.dto.ProgramTradingHistoryItem;
import dev.eolmae.marketmonitor.domain.view.dto.ProgramTradingRankingItem;
import dev.eolmae.marketmonitor.domain.view.dto.ShortSellingHistoryItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.dto.StockHistoryResponse;
import dev.eolmae.marketmonitor.domain.view.dto.StockResponse;
import dev.eolmae.marketmonitor.domain.view.dto.WatchStockResponse;
import dev.eolmae.marketmonitor.domain.view.enums.IntradayInvestorQuery;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRankingType;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRankingType;
import dev.eolmae.marketmonitor.domain.stock.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.IntradayInvestorRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.InvestorTradingSummarySnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ShortSellingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.StockInfoRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockCacheService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketQueryService {

    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;
    private final InvestorTradingSummarySnapshotRepository investorTradingSummarySnapshotRepository;
    private final IntradayInvestorRankingSnapshotRepository intradayInvestorRankingSnapshotRepository;
    private final ProgramTradingRankingSnapshotRepository programTradingRankingSnapshotRepository;
    private final IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository;
    private final StockInfoRepository stockInfoRepository;
    private final ProgramTradingHistoryRepository programTradingHistoryRepository;
    private final ProgramTradingDailyHistoryRepository programTradingDailyHistoryRepository;
    private final ShortSellingDailyHistoryRepository shortSellingDailyHistoryRepository;
    private final WatchStockCacheService watchStockCacheService;
    private final StockInfoCacheService stockInfoCacheService;

    private record SummaryDefaults(
            Market market, AmtQtyType amtQty, IntradayInvestorType investor, IntradayRankingType ranking) {}

    private static final SummaryDefaults SUMMARY_DEFAULTS = new SummaryDefaults(
            Market.KOSPI, AmtQtyType.AMOUNT, IntradayInvestorType.FOREIGNER, IntradayRankingType.NET_BUY);

    public MarketSummaryResponse getMarketSummary() {
        var snapshotTime =
                marketOverviewSnapshotRepository.findLatestSnapshotTime().orElse(null);

        // 아직 수집된 데이터가 없으면 빈 응답 반환
        if (snapshotTime == null) {
            return new MarketSummaryResponse(
                    null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), getWatchStocks());
        } // TODO 죄다 null 인 파라미터로 객체 생성할바에 record 말고 그냥 class 사용은 어떤지, 혹은 record 내부에 위 메서드를 생성하는 안 은 어떤지.

        var marketOverviews = marketOverviewSnapshotRepository.findBySnapshotTimeOrderByMarketTypeAsc(snapshotTime);

        var investorSummaries =
                investorTradingSummarySnapshotRepository.findBySnapshotTimeOrderByMarketTypeAscInvestorTypeAsc(
                        snapshotTime);

        var lastCollectedAt = marketOverviews.stream()
                .map(MarketOverviewSnapshot::getLastCollectedAt)
                .max(LocalDateTime::compareTo)
                .orElseThrow();

        var marketStatus = marketOverviews.stream()
                .max(Comparator.comparing(overview -> overview.getChangeRate().abs()))
                .map(MarketOverviewSnapshot::getMarketStatus)
                .orElse("UNKNOWN");

        // 대시보드 요약: KOSPI 기준 외국인 순매수 상위 (대표 조합)
        var intradayItems = intradayInvestorRankingSnapshotRepository
                .findBySnapshotTimeAndMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
                        snapshotTime,
                        List.of(SUMMARY_DEFAULTS.market()),
                        List.of(SUMMARY_DEFAULTS.investor()),
                        SUMMARY_DEFAULTS.ranking(),
                        SUMMARY_DEFAULTS.amtQty())
                .stream()
                .map(item -> new IntradayInvestorRankingItem(
                        item.getMarketType(),
                        item.getInvestorType(),
                        item.getRank(),
                        item.getStockCode(),
                        item.getStockName(),
                        item.getNetBuyAmount(),
                        item.getTradedVolume()))
                .toList();

        // 대시보드 요약: 프로그램 순매수 상위 (KOSPI 기준 금액)
        var programItems = getProgramTradingRankings(MarketQuery.KOSPI, ProgramRankingType.NET_BUY, SUMMARY_DEFAULTS.amtQty())
                .items();

        // 대시보드 요약: KOSPI 지수 기여도 상위
        var indexContributionItems =
                indexContributionRankingSnapshotRepository
                        .findBySnapshotTimeAndMarketTypeOrderByRankAsc(snapshotTime, SUMMARY_DEFAULTS.market())
                        .stream()
                        .map(item -> new IndexContributionItem(
                                item.getMarketType(),
                                item.getRank(),
                                item.getStockCode(),
                                item.getStockName(),
                                item.getContributionScore(),
                                item.getPriceChangeRate()))
                        .toList();

        return new MarketSummaryResponse(
                snapshotTime,
                lastCollectedAt,
                marketStatus,
                marketOverviews.stream()
                        .map(item -> new MarketOverviewItem(
                                item.getMarketType(),
                                item.getMarketStatus(),
                                item.getIndexValue(),
                                item.getChangeValue(),
                                item.getChangeRate(),
                                item.getTradingValue(),
                                item.getUpperLimitCount(),
                                item.getLowerLimitCount(),
                                item.getAdvancers(),
                                item.getDecliners(),
                                item.getUnchangedCount()))
                        .toList(),
                investorSummaries.stream()
                        .map(item -> new InvestorTradingSummaryItem(
                                item.getMarketType(),
                                item.getInvestorType(),
                                item.getBuyAmount(),
                                item.getSellAmount(),
                                item.getNetBuyAmount()))
                        .toList(),
                intradayItems,
                programItems,
                indexContributionItems,
                getWatchStocks());
    }

    // TODO 화면에서 미사용 확인됨 (컨트롤러 #3과 동일) — 화면 점검 후 이상 없으면 삭제
    //    public SnapshotResponse<IntradayInvestorRankingItem> getIntradayRankings(
    //            Market marketType, IntradayInvestorType investorType, IntradayRankingType rankingType) {
    //        var snapshotTime = intradayInvestorRankingSnapshotRepository
    //                .findLatestSnapshotTime()
    //                .orElse(null);
    //        if (snapshotTime == null) {
    //            return new SnapshotResponse<>(null, List.of());
    //        }
    //
    //        var items = intradayInvestorRankingSnapshotRepository
    //                .findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
    //                        snapshotTime, marketType, investorType, rankingType)
    //                .stream()
    //                .limit(10)
    //                .map(item -> new IntradayInvestorRankingItem(
    //                        item.getMarketType(),
    //                        item.getInvestorType(),
    //                        item.getRank(),
    //                        item.getStockCode(),
    //                        item.getStockName(),
    //                        item.getNetBuyAmount(),
    //                        item.getTradedVolume()))
    //                .toList();
    //
    //        return new SnapshotResponse<>(snapshotTime, items);
    //    }

    public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            MarketQuery marketType, ProgramRankingType rankingType, AmtQtyType amtQtyType) {
        var snapshotTime =
                programTradingRankingSnapshotRepository.findLatestSnapshotTime().orElse(null);
        if (snapshotTime == null) {
            return new SnapshotResponse<>(null, List.of());
        }

        var snapshots = programTradingRankingSnapshotRepository
                .findBySnapshotTimeAndMarketTypeInAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
                        snapshotTime, marketType.convert(), rankingType, amtQtyType)
                .stream()
                .sorted(Comparator.comparing(ProgramTradingRankingSnapshot::getProgramNetBuyAmount)
                        .reversed())
                .toList();

        return new SnapshotResponse<>(
                snapshotTime,
                snapshots.stream()
                        .limit(10)
                        .map(item -> new ProgramTradingRankingItem(
                                item.getRank(),
                                item.getStockCode(),
                                item.getStockName(),
                                item.getProgramBuyAmount(),
                                item.getProgramSellAmount(),
                                item.getProgramNetBuyAmount()))
                        .toList());
    }

    public SnapshotResponse<IndexContributionItem> getIndexContribution(Market marketType) {
        var snapshotTime = indexContributionRankingSnapshotRepository
                .findLatestSnapshotTime()
                .orElse(null);
        if (snapshotTime == null) {
            return new SnapshotResponse<>(null, List.of());
        }

        var items =
                indexContributionRankingSnapshotRepository
                        .findBySnapshotTimeAndMarketTypeOrderByRankAsc(snapshotTime, marketType)
                        .stream()
                        .map(item -> new IndexContributionItem(
                                item.getMarketType(),
                                item.getRank(),
                                item.getStockCode(),
                                item.getStockName(),
                                item.getContributionScore(),
                                item.getPriceChangeRate()))
                        .toList();

        return new SnapshotResponse<>(snapshotTime, items);
    }

    public List<WatchStockResponse> getWatchStocks() {
        List<WatchStock> watchStockCache = watchStockCacheService.getCache();
        boolean isPrimaryRegistered = watchStockCache.stream().anyMatch(WatchStock::isPrimary);
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();

        return watchStockCache.stream()
                .map(ws -> {
                    StockInfo stockInfo = stockInfoCache.get(ws.getStockCode());
                    return new WatchStockResponse(
                            ws.getStockCode(),
                            stockInfo.getStockName(),
                            stockInfo.getMarketType(),
                            ws.isPrimary(),
                            isPrimaryRegistered ? ws.isPrimary() : Integer.valueOf(1).equals(ws.getHoldingRank()));
                })
                .toList();

    }

    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistory(String stockCode) {
        return getProgramTradingHistoryByRange(
                stockCode, LocalDate.now().atStartOfDay(), LocalDate.now().atTime(LocalTime.MAX));
    }

    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistoryByRange(
            String stockCode, LocalDateTime from, LocalDateTime to) {
        return new StockHistoryResponse<>(
                stockCode,
                programTradingHistoryRepository
                        .findByStockCodeAndSnapshotTimeBetweenOrderBySnapshotTimeAsc(stockCode, from, to)
                        .stream()
                        .map(item -> new ProgramTradingHistoryItem(
                                item.getSnapshotTime(),
                                item.getProgramBuyAmount(),
                                item.getProgramSellAmount(),
                                item.getProgramNetBuyAmount()))
                        .toList());
    }

    public StockHistoryResponse<ProgramTradingDailyHistoryItem> getProgramTradingDailyHistory(
            String stockCode, LocalDate from, LocalDate to) {
        return new StockHistoryResponse<>(
                stockCode,
                programTradingDailyHistoryRepository
                        .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, from, to)
                        .stream()
                        .map(item -> new ProgramTradingDailyHistoryItem(
                                item.getTradeDate(),
                                item.getProgramBuyAmount(),
                                item.getProgramSellAmount(),
                                item.getProgramNetBuyAmount()))
                        .toList());
    }

    /** 공매도 추이 최신 10건 반환 */
    public StockHistoryResponse<ShortSellingHistoryItem> getShortSellingHistory(String stockCode) {
        var items = shortSellingDailyHistoryRepository.findByStockCodeOrderByTradeDateDesc(stockCode).stream()
                .limit(10)
                .map(item -> new ShortSellingHistoryItem(
                        item.getTradeDate(),
                        null,
                        item.getClosePrice(),
                        item.getPriceChange(),
                        item.getChangeRate(),
                        item.getTradingVolume(),
                        item.getShortVolume(),
                        item.getCumulativeShortVolume(),
                        item.getShortRatio(),
                        item.getShortAmount(),
                        item.getShortAvgPrice()))
                .toList();

        return new StockHistoryResponse<>(stockCode, items);
    }

    /**
     * 장중 투자자별 매매 상위 top 10 반환.
     * <ul>
     *   <li>{@code market=COMBINED}: KOSPI+KOSDAQ 스냅샷 종목코드 기준 합산 후 재정렬</li>
     *   <li>{@code investor=FOREIGN_COMBINED}: FOREIGNER+FOREIGN_COMPANY 종목코드 기준 합산 후 재정렬</li>
     *   <li>{@code ranking=NET_SELL}: netBuyAmount 절댓값 변환 후 반환</li>
     * </ul>
     */
    public SnapshotResponse<IntradayInvestorSummaryItem> getIntradayTop(
            MarketQuery market, IntradayInvestorQuery investor, IntradayRankingType ranking, AmtQtyType amtQty) {
        var snapshotTime = intradayInvestorRankingSnapshotRepository
                .findLatestSnapshotTime()
                .orElse(null);
        if (snapshotTime == null) {
            return new SnapshotResponse<>(null, List.of());
        }

        List<Market> markets = market.convert();
        List<IntradayInvestorType> investors = investor.convert();

        // market·investor를 IN으로 한 번에 조회 → stockCode 기준 합산
        Map<String, BigDecimal> netByStock = new HashMap<>();
        Map<String, String> nameByStock = new HashMap<>();

        intradayInvestorRankingSnapshotRepository
                .findBySnapshotTimeAndMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
                        snapshotTime, markets, investors, ranking, amtQty)
                .forEach(s -> {
                    netByStock.merge(s.getStockCode(), s.getNetBuyAmount(), BigDecimal::add);
                    nameByStock.putIfAbsent(s.getStockCode(), s.getStockName());
                });

        boolean isNetSell = ranking == IntradayRankingType.NET_SELL;

        // 순매수: netBuyAmount 내림차순 / 순매도: netBuyAmount 오름차순(가장 많이 매도한 순) → 절댓값 반환
        Comparator<Map.Entry<String, BigDecimal>> comparator = isNetSell
                ? Map.Entry.comparingByValue() // 가장 작은(음수) 순
                : Map.Entry.<String, BigDecimal>comparingByValue().reversed();

        List<IntradayInvestorSummaryItem> items = netByStock.entrySet().stream()
                .sorted(comparator)
                .limit(10)
                .map(e -> new IntradayInvestorSummaryItem(
                        e.getKey(),
                        nameByStock.get(e.getKey()),
                        isNetSell ? e.getValue().abs() : e.getValue()))
                .toList();

        return new SnapshotResponse<>(snapshotTime, items);
    }

    /** 활성 종목 전체 반환 — 관심종목 등록 화면 자동완성용 */
    public List<StockResponse> getAllStocks() {
        return stockInfoRepository.findByActiveTrueOrderByStockCodeAsc().stream()
                .map(s -> new StockResponse(s.getStockCode(), s.getStockName(), s.getMarketType()))
                .toList();
    }
}
