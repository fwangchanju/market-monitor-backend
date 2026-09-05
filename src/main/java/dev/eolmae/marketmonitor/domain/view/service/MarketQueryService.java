package dev.eolmae.marketmonitor.domain.view.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.IndexContributionRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.IntradayInvestorRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.InvestorTradingSummarySnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingDailyHistory;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQty;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestor;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRanking;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRanking;
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
import dev.eolmae.marketmonitor.domain.stock.util.CollectionChecker;
import dev.eolmae.marketmonitor.domain.view.dto.IndexContributionItem;
import dev.eolmae.marketmonitor.domain.view.dto.IntradayInvestorSummaryItem;
import dev.eolmae.marketmonitor.domain.view.dto.InvestorTradingSummaryItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketOverviewItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketSummaryResponse;
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
import dev.eolmae.marketmonitor.domain.view.enums.RankingType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
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

    private static final int RANKING_LIMIT = 10;

    /** 메인 대시보드용 시장 종합 현황 반환 — HTS에서 화면 여러 개를 오가며 보던 표 7개를 한 화면에 모은 것 */
    public MarketSummaryResponse getMarketSummary() {
        record SummaryDefaults(Market market, AmtQty amtQty, IntradayInvestor investor, RankingType ranking) {}

        var summaryDefaults =
                new SummaryDefaults(Market.KOSPI, AmtQty.AMOUNT, IntradayInvestor.FOREIGNER, RankingType.NET_BUY);

        return new MarketSummaryResponse(
                getMarketOverviews(),
                getInvestorTradingSummaries(),
                getIntradayTop(
                        List.of(summaryDefaults.market()),
                        List.of(summaryDefaults.investor()),
                        IntradayRanking.from(summaryDefaults.ranking()),
                        summaryDefaults.amtQty()),
                getProgramTradingRankings(
                        summaryDefaults.market(), summaryDefaults.ranking(), summaryDefaults.amtQty()),
                getIndexContribution(summaryDefaults.market()),
                getMainShortSellingHistory(),
                getMainProgramTradingHistory());
    }

    /** 관심종목 중 대표 종목의 공매도 추이 반환 — 대표 종목 없으면 빈 값 */
    private StockHistoryResponse<ShortSellingHistoryItem> getMainShortSellingHistory() {
        String mainStockCode = findMainStockCode();
        if (mainStockCode == null) {
            return StockHistoryResponse.empty();
        }
        return getShortSellingHistory(mainStockCode);
    }

    /** 관심종목 중 대표 종목의 프로그램매매 추이(장중) 반환 — 대표 종목 없으면 빈 값 */
    private StockHistoryResponse<ProgramTradingHistoryItem> getMainProgramTradingHistory() {
        String mainStockCode = findMainStockCode();
        if (mainStockCode == null) {
            return StockHistoryResponse.empty();
        }
        return getProgramTradingHistory(mainStockCode);
    }

    /** 관심종목 중 대표 종목 코드 반환 — 등록된 게 없으면 null */
    private String findMainStockCode() {
        return getWatchStocks().stream()
                .filter(WatchStockResponse::isMain)
                .map(WatchStockResponse::stockCode)
                .findFirst()
                .orElse(null);
    }

    /** 대시보드 요약: 시장 종합 현황 (지수/등락/상하한가) */
    private SnapshotResponse<MarketOverviewItem> getMarketOverviews() {
        LocalDateTime latestSnapshotTime = marketOverviewSnapshotRepository
                .findFirstByOrderBySnapshotTimeDesc()
                .map(MarketOverviewSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        // marketType은 @Enumerated(STRING)이라 DB ORDER BY는 문자열 순(KOSDAQ < KOSPI)이 됨 -
        // enum 선언 순서(KOSPI, KOSDAQ)대로 나오도록 Java에서 재정렬.
        var snapshots = marketOverviewSnapshotRepository.findBySnapshotTime(latestSnapshotTime).stream()
                .sorted(Comparator.comparing(MarketOverviewSnapshot::getMarketType))
                .toList();

        return new SnapshotResponse<>(
                CollectionChecker.expectedSnapshotTime(),
                snapshots.stream()
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
                                item.getUnchangedCount(),
                                item.getSnapshotTime()))
                        .toList());
    }

    /** 대시보드 요약: 투자자별 매매 종합 */
    private SnapshotResponse<InvestorTradingSummaryItem> getInvestorTradingSummaries() {
        LocalDateTime latestSnapshotTime = investorTradingSummarySnapshotRepository
                .findFirstByOrderBySnapshotTimeDesc()
                .map(InvestorTradingSummarySnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        // marketType/investor 둘 다 @Enumerated(STRING)이라 DB ORDER BY는 문자열 순이 됨 -
        // enum 선언 순서(시장: KOSPI, KOSDAQ / 투자자: PERSONAL, FOREIGNER, ...)대로 Java에서 재정렬.
        var snapshots = investorTradingSummarySnapshotRepository.findBySnapshotTime(latestSnapshotTime).stream()
                .sorted(Comparator.comparing(InvestorTradingSummarySnapshot::getMarketType)
                        .thenComparing(InvestorTradingSummarySnapshot::getInvestor))
                .toList();

        return new SnapshotResponse<>(
                CollectionChecker.expectedSnapshotTime(),
                snapshots.stream()
                        .map(item -> new InvestorTradingSummaryItem(
                                item.getMarketType(),
                                item.getInvestor(),
                                item.getBuyAmount(),
                                item.getSellAmount(),
                                item.getNetBuyAmount(),
                                item.getSnapshotTime()))
                        .toList());
    }

    public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            MarketQuery market, RankingType ranking, AmtQty amtQty) {
        return getProgramTradingRankings(market.toMarkets(), ProgramRanking.from(ranking), amtQty);
    }

    private SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            Market market, RankingType ranking, AmtQty amtQty) {
        return getProgramTradingRankings(List.of(market), ProgramRanking.from(ranking), amtQty);
    }

    /** 프로그램매매 순매수/순매도 상위 top 10 반환 */
    private SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            List<Market> markets, ProgramRanking ranking, AmtQty amtQty) {
        LocalDateTime latestSnapshotTime = programTradingRankingSnapshotRepository
                .findFirstByMarketTypeInAndRankingTypeAndAmtQtyOrderBySnapshotTimeDesc(markets, ranking, amtQty)
                .map(ProgramTradingRankingSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        var snapshots =
                programTradingRankingSnapshotRepository.findByMarketTypeInAndRankingTypeAndAmtQtyAndSnapshotTime(
                        markets, ranking, amtQty, latestSnapshotTime);

        // 종목당 거래소(exchangeType)별로 행이 나뉘어 있어 종목 기준으로 금액을 합산한 뒤,
        // market=ALL_STOCK(KOSPI+KOSDAQ)까지 포함해 netBuyAmount 기준으로 다시 정렬
        Map<String, AggregatedProgramRanking> merged = snapshots.stream()
                .collect(Collectors.toMap(
                        ProgramTradingRankingSnapshot::getStockCode,
                        MarketQueryService::toAggregated,
                        MarketQueryService::mergeAggregated));

        var sorted = merged.values().stream()
                .sorted(Comparator.comparing(AggregatedProgramRanking::netBuyAmount, ranking.valueComparator()))
                .limit(RANKING_LIMIT)
                .toList();

        List<ProgramTradingRankingItem> items = new ArrayList<>();
        int rank = 1;
        for (AggregatedProgramRanking agg : sorted) {
            items.add(new ProgramTradingRankingItem(
                    rank++,
                    agg.stockCode(),
                    agg.stockName(),
                    agg.buyAmount(),
                    agg.sellAmount(),
                    ranking.normalize(agg.netBuyAmount()),
                    agg.snapshotTime()));
        }

        return new SnapshotResponse<>(CollectionChecker.expectedSnapshotTime(), items);
    }

    private static AggregatedProgramRanking toAggregated(ProgramTradingRankingSnapshot snapshot) {
        return new AggregatedProgramRanking(
                snapshot.getStockCode(),
                snapshot.getStockName(),
                snapshot.getProgramBuyAmount(),
                snapshot.getProgramSellAmount(),
                snapshot.getProgramNetBuyAmount(),
                snapshot.getSnapshotTime());
    }

    private static AggregatedProgramRanking mergeAggregated(AggregatedProgramRanking a, AggregatedProgramRanking b) {
        return new AggregatedProgramRanking(
                a.stockCode(),
                a.stockName(),
                a.buyAmount().add(b.buyAmount()),
                a.sellAmount().add(b.sellAmount()),
                a.netBuyAmount().add(b.netBuyAmount()),
                a.snapshotTime());
    }

    private record AggregatedProgramRanking(
            String stockCode,
            String stockName,
            BigDecimal buyAmount,
            BigDecimal sellAmount,
            BigDecimal netBuyAmount,
            LocalDateTime snapshotTime) {}

    /** 지수 기여도 상위 종목 반환 */
    public SnapshotResponse<IndexContributionItem> getIndexContribution(Market market) {
        LocalDateTime latestSnapshotTime = indexContributionRankingSnapshotRepository
                .findFirstByMarketTypeOrderBySnapshotTimeDesc(market)
                .map(IndexContributionRankingSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        var snapshots = indexContributionRankingSnapshotRepository.findByMarketTypeAndSnapshotTimeOrderByRankAsc(
                market, latestSnapshotTime);

        var items = snapshots.stream()
                .map(item -> new IndexContributionItem(
                        item.getMarketType(),
                        item.getRank(),
                        item.getStockCode(),
                        item.getStockName(),
                        item.getContributionScore(),
                        item.getPriceChangeRate(),
                        item.getSnapshotTime()))
                .toList();

        return new SnapshotResponse<>(CollectionChecker.expectedSnapshotTime(), items);
    }

    /** 관심종목 목록 반환 */
    public List<WatchStockResponse> getWatchStocks() {
        List<WatchStock> watchStockCache = watchStockCacheService.getCache();
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        boolean isPrimaryRegistered = watchStockCache.stream().anyMatch(WatchStock::isPrimary);

        return watchStockCache.stream()
                .map(ws -> {
                    StockInfo stockInfo = stockInfoCache.get(ws.getStockCode());
                    return new WatchStockResponse(
                            ws.getStockCode(),
                            stockInfo.getStockName(),
                            stockInfo.getMarketType(),
                            isPrimaryRegistered ? ws.isPrimary() : ws.isTopHoldingRank(),
                            ws.getRegisterBy());
                })
                .toList();
    }

    /**
     * 종목별 프로그램매매 이력 최신 20건 반환.
     * programTradeIntradayCollector 비활성화(관심종목 구조 정리 전까지)로 신규 데이터가 없어, 오래된
     * 데이터를 최신인 것처럼 계속 보여주지 않도록 조회 없이 항상 빈 값을 반환한다.
     */
    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistory(String stockCode) {
        // programTradingHistoryRepository.findRecentByStockCode(stockCode).stream()
        //         .map(item -> new ProgramTradingHistoryItem(
        //                 item.getSnapshotTime(),
        //                 item.getProgramBuyAmount(),
        //                 item.getProgramSellAmount(),
        //                 item.getProgramNetBuyAmount()))
        //         .toList();
        return StockHistoryResponse.empty();
    }

    /**
     * 종목별 프로그램매매 이력(일별) 최신 20건 반환.
     * programTradeDailyCollector 비활성화(관심종목 구조 정리 전까지)로 신규 데이터가 없어, 오래된
     * 데이터를 최신인 것처럼 계속 보여주지 않도록 조회 없이 항상 빈 값을 반환한다.
     */
    public StockHistoryResponse<ProgramTradingDailyHistoryItem> getProgramTradingDailyHistory(String stockCode) {
        // return toProgramTradingDailyHistoryResponse(
        //         stockCode, programTradingDailyHistoryRepository.findRecentByStockCode(stockCode));
        return StockHistoryResponse.empty();
    }

    private StockHistoryResponse<ProgramTradingDailyHistoryItem> toProgramTradingDailyHistoryResponse(
            String stockCode, List<ProgramTradingDailyHistory> history) {
        return new StockHistoryResponse<>(
                stockCode,
                CollectionChecker.expectedSnapshotTime(),
                history.stream()
                        .map(item -> new ProgramTradingDailyHistoryItem(
                                item.getTradeDate(),
                                item.getProgramBuyAmount(),
                                item.getProgramSellAmount(),
                                item.getProgramNetBuyAmount()))
                        .toList());
    }

    /**
     * 공매도 추이 최신 20건 반환.
     * shortSellingTrendCollector 비활성화(관심종목 구조 정리 전까지)로 신규 데이터가 없어, 오래된
     * 데이터를 최신인 것처럼 계속 보여주지 않도록 조회 없이 항상 빈 값을 반환한다.
     */
    public StockHistoryResponse<ShortSellingHistoryItem> getShortSellingHistory(String stockCode) {
        // shortSellingDailyHistoryRepository.findRecentByStockCode(stockCode).stream()
        //         .map(item -> new ShortSellingHistoryItem(
        //                 item.getTradeDate(),
        //                 item.getClosePrice(),
        //                 item.getPriceChange(),
        //                 item.getChangeRate(),
        //                 item.getTradingVolume(),
        //                 item.getShortVolume(),
        //                 item.getCumulativeShortVolume(),
        //                 item.getShortRatio(),
        //                 item.getShortAmount(),
        //                 item.getShortAvgPrice()))
        //         .toList();
        return StockHistoryResponse.empty();
    }

    public SnapshotResponse<IntradayInvestorSummaryItem> getIntradayTop(
            MarketQuery market, IntradayInvestorQuery investor, RankingType ranking, AmtQty amtQty) {
        return getIntradayTop(market.toMarkets(), investor.toInvestors(), IntradayRanking.from(ranking), amtQty);
    }

    /**
     * 장중 투자자별 매매 상위 top 10 반환.
     * intradayInvestorRankingCollector 비활성화(관심종목 구조 정리 전까지)로 신규 데이터가 없어, 오래된
     * 데이터를 최신인 것처럼 계속 보여주지 않도록 조회 없이 항상 빈 값을 반환한다.
     */
    private SnapshotResponse<IntradayInvestorSummaryItem> getIntradayTop(
            List<Market> markets, List<IntradayInvestor> investors, IntradayRanking ranking, AmtQty amtQty) {
        // record StockNet(String stockCode, String stockName, BigDecimal netAmount) {}
        //
        // LocalDateTime latestSnapshotTime = intradayInvestorRankingSnapshotRepository
        //         .findFirstByMarketTypeInAndInvestorInAndRankingTypeAndAmtQtyOrderBySnapshotTimeDesc(
        //                 markets, investors, ranking, amtQty)
        //         .map(IntradayInvestorRankingSnapshot::getSnapshotTime)
        //         .orElse(null);
        // if (latestSnapshotTime == null) {
        //     return SnapshotResponse.empty();
        // }
        //
        // List<IntradayInvestorRankingSnapshot> snapshots =
        //         intradayInvestorRankingSnapshotRepository
        //                 .findByMarketTypeInAndInvestorInAndRankingTypeAndAmtQtyAndSnapshotTimeOrderByRankAsc(
        //                         markets, investors, ranking, amtQty, latestSnapshotTime);
        //
        // // investor=FOREIGN_COMBINED(외국인+외국계)처럼 같은 stockCode가 여러 row로 들어올 때 stockCode 기준 합산
        // Map<String, BigDecimal> netByStock = new HashMap<>();
        // Map<String, String> nameByStock = new HashMap<>();
        // snapshots.forEach(s -> {
        //     netByStock.merge(s.getStockCode(), s.getNetBuyAmount(), BigDecimal::add);
        //     nameByStock.putIfAbsent(s.getStockCode(), s.getStockName());
        // });
        //
        // List<StockNet> netList = netByStock.entrySet().stream()
        //         .map(e -> new StockNet(e.getKey(), nameByStock.get(e.getKey()), e.getValue()))
        //         .toList();
        //
        // // ranking=NET_SELL이면 정렬 방향 반전 + netBuyAmount 절댓값 변환
        // List<IntradayInvestorSummaryItem> items = netList.stream()
        //         .sorted(Comparator.comparing(StockNet::netAmount, ranking.valueComparator()))
        //         .limit(RANKING_LIMIT)
        //         .map(s -> new IntradayInvestorSummaryItem(
        //                 s.stockCode(), s.stockName(), ranking.normalize(s.netAmount()), latestSnapshotTime))
        //         .toList();
        //
        // return new SnapshotResponse<>(CollectionChecker.expectedSnapshotTime(), items);
        return SnapshotResponse.empty();
    }

    /** 활성 종목 전체 반환 — 관심종목 등록 화면 자동완성용 */
    public List<StockResponse> getAllStocks() {
        return stockInfoRepository.findByActiveTrue().stream()
                .map(s -> new StockResponse(s.getStockCode(), s.getStockName(), s.getMarketType()))
                .toList();
    }
}
