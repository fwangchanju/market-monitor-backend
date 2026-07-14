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
import dev.eolmae.marketmonitor.domain.view.enums.RankingType;
import dev.eolmae.marketmonitor.domain.stock.entity.IndexContributionRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.IntradayInvestorRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.InvestorTradingSummarySnapshot;
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

    private static final int RANKING_LIMIT = 10;

    /** 메인 대시보드용 시장 종합 현황 반환 */
    public MarketSummaryResponse getMarketSummary() {
        record SummaryDefaults(Market market, AmtQtyType amtQty, IntradayInvestorType investor, RankingType ranking) {}

        var summaryDefaults = new SummaryDefaults(
                Market.KOSPI, AmtQtyType.AMOUNT, IntradayInvestorType.FOREIGNER, RankingType.NET_BUY);

        return new MarketSummaryResponse(
                getMarketOverviews(),
                getInvestorTradingSummaries(),
                getIntradaySummaryHighlights(
                        summaryDefaults.market(),
                        summaryDefaults.investor(),
                        summaryDefaults.ranking(),
                        summaryDefaults.amtQty()),
                getProgramTradingRankings(
                        summaryDefaults.market(),
                        summaryDefaults.ranking(),
                        summaryDefaults.amtQty()),
                getIndexContribution(summaryDefaults.market()));
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

        var snapshots = marketOverviewSnapshotRepository.findBySnapshotTimeOrderByMarketTypeAsc(latestSnapshotTime);

        return new SnapshotResponse<>(
                latestSnapshotTime,
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
                                item.getLastCollectedAt()))
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

        var snapshots = investorTradingSummarySnapshotRepository.findBySnapshotTimeOrderByMarketTypeAscInvestorTypeAsc(
                latestSnapshotTime);

        return new SnapshotResponse<>(
                latestSnapshotTime,
                snapshots.stream()
                        .map(item -> new InvestorTradingSummaryItem(
                                item.getMarketType(),
                                item.getInvestorType(),
                                item.getBuyAmount(),
                                item.getSellAmount(),
                                item.getNetBuyAmount()))
                        .toList());
    }

    private SnapshotResponse<IntradayInvestorRankingItem> getIntradaySummaryHighlights(
            Market market, IntradayInvestorType investor, RankingType ranking, AmtQtyType amtQty) {
        return getIntradaySummaryHighlights(
                List.of(market), List.of(investor), IntradayRankingType.from(ranking), amtQty);
    }

    /** 대시보드 요약: KOSPI 기준 외국인 순매수 상위 (대표 조합) */
    private SnapshotResponse<IntradayInvestorRankingItem> getIntradaySummaryHighlights(
            List<Market> markets, List<IntradayInvestorType> investors, IntradayRankingType ranking, AmtQtyType amtQty) {
        LocalDateTime latestSnapshotTime = intradayInvestorRankingSnapshotRepository
                .findFirstByMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeOrderBySnapshotTimeDesc(
                        markets, investors, ranking, amtQty)
                .map(IntradayInvestorRankingSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        var snapshots = intradayInvestorRankingSnapshotRepository
                .findByMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeAndSnapshotTimeOrderByRankAsc(
                        markets, investors, ranking, amtQty, latestSnapshotTime);

        return new SnapshotResponse<>(
                latestSnapshotTime,
                snapshots.stream()
                        .map(item -> new IntradayInvestorRankingItem(
                                item.getMarketType(),
                                item.getInvestorType(),
                                item.getRank(),
                                item.getStockCode(),
                                item.getStockName(),
                                item.getNetBuyAmount(),
                                item.getTradedVolume()))
                        .toList());
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
            MarketQuery market, RankingType ranking, AmtQtyType amtQty) {
        return getProgramTradingRankings(market.toMarkets(), ProgramRankingType.from(ranking), amtQty);
    }

    private SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            Market market, RankingType ranking, AmtQtyType amtQty) {
        return getProgramTradingRankings(List.of(market), ProgramRankingType.from(ranking), amtQty);
    }

    /** 프로그램매매 순매수/순매도 상위 top 10 반환 */
    private SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            List<Market> markets, ProgramRankingType ranking, AmtQtyType amtQty) {
        LocalDateTime latestSnapshotTime = programTradingRankingSnapshotRepository
                .findFirstByMarketTypeInAndRankingTypeAndAmtQtyTypeOrderBySnapshotTimeDesc(markets, ranking, amtQty)
                .map(ProgramTradingRankingSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        var snapshots = programTradingRankingSnapshotRepository
                .findByMarketTypeInAndRankingTypeAndAmtQtyTypeAndSnapshotTimeOrderByRankAsc(
                        markets, ranking, amtQty, latestSnapshotTime);

        // market=COMBINED(KOSPI+KOSDAQ)면 마켓별 rank를 합쳐 netBuyAmount 기준으로 다시 정렬
        var sorted = snapshots.stream()
                .sorted(Comparator.comparing(
                        ProgramTradingRankingSnapshot::getProgramNetBuyAmount, ranking.valueComparator()))
                .toList();

        return new SnapshotResponse<>(
                latestSnapshotTime,
                sorted.stream()
                        .limit(RANKING_LIMIT)
                        .map(item -> new ProgramTradingRankingItem(
                                item.getRank(),
                                item.getStockCode(),
                                item.getStockName(),
                                item.getProgramBuyAmount(),
                                item.getProgramSellAmount(),
                                ranking.normalize(item.getProgramNetBuyAmount())))
                        .toList());
    }

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
                        item.getPriceChangeRate()))
                .toList();

        return new SnapshotResponse<>(latestSnapshotTime, items);
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
                            isPrimaryRegistered ? ws.isPrimary() : ws.isTopHoldingRank());
                })
                .toList();
    }

    /** 종목별 당일 프로그램매매 이력 반환 */
    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistory(String stockCode) {
        return getProgramTradingHistoryByRange(
                stockCode, LocalDate.now().atStartOfDay(), LocalDate.now().atTime(LocalTime.MAX));
    }

    /** 종목별 기간별 프로그램매매 이력 반환 */
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

    /** 일별 데이터는 계속 수집 중이나, 별도 대시보드 화면이 필요 없어 컨트롤러 엔드포인트는 아직 없음 */
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
        return new StockHistoryResponse<>(
                stockCode,
                shortSellingDailyHistoryRepository.findByStockCodeOrderByTradeDateDesc(stockCode).stream()
                        .limit(RANKING_LIMIT)
                        .map(item -> new ShortSellingHistoryItem(
                                item.getTradeDate(),
                                item.getClosePrice(),
                                item.getPriceChange(),
                                item.getChangeRate(),
                                item.getTradingVolume(),
                                item.getShortVolume(),
                                item.getCumulativeShortVolume(),
                                item.getShortRatio(),
                                item.getShortAmount(),
                                item.getShortAvgPrice()))
                        .toList());
    }

    public SnapshotResponse<IntradayInvestorSummaryItem> getIntradayTop(
            MarketQuery market, IntradayInvestorQuery investor, RankingType ranking, AmtQtyType amtQty) {
        return getIntradayTop(
                market.toMarkets(), investor.toInvestorTypes(), IntradayRankingType.from(ranking), amtQty);
    }

    /** 장중 투자자별 매매 상위 top 10 반환 */
    private SnapshotResponse<IntradayInvestorSummaryItem> getIntradayTop(
            List<Market> markets, List<IntradayInvestorType> investors, IntradayRankingType ranking, AmtQtyType amtQty) {
        record StockNet(String stockCode, String stockName, BigDecimal netAmount) {}

        LocalDateTime latestSnapshotTime = intradayInvestorRankingSnapshotRepository
                .findFirstByMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeOrderBySnapshotTimeDesc(
                        markets, investors, ranking, amtQty)
                .map(IntradayInvestorRankingSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        List<IntradayInvestorRankingSnapshot> snapshots = intradayInvestorRankingSnapshotRepository
                .findByMarketTypeInAndInvestorTypeInAndRankingTypeAndAmtQtyTypeAndSnapshotTimeOrderByRankAsc(
                        markets, investors, ranking, amtQty, latestSnapshotTime);

        // investor=FOREIGN_COMBINED(외국인+외국계)처럼 같은 stockCode가 여러 row로 들어올 때 stockCode 기준 합산
        Map<String, BigDecimal> netByStock = new HashMap<>();
        Map<String, String> nameByStock = new HashMap<>();
        snapshots.forEach(s -> {
            netByStock.merge(s.getStockCode(), s.getNetBuyAmount(), BigDecimal::add);
            nameByStock.putIfAbsent(s.getStockCode(), s.getStockName());
        });

        List<StockNet> netList = netByStock.entrySet().stream()
                .map(e -> new StockNet(e.getKey(), nameByStock.get(e.getKey()), e.getValue()))
                .toList();

        // ranking=NET_SELL이면 정렬 방향 반전 + netBuyAmount 절댓값 변환
        List<IntradayInvestorSummaryItem> items = netList.stream()
                .sorted(Comparator.comparing(StockNet::netAmount, ranking.valueComparator()))
                .limit(RANKING_LIMIT)
                .map(s -> new IntradayInvestorSummaryItem(
                        s.stockCode(), s.stockName(), ranking.normalize(s.netAmount())))
                .toList();

        return new SnapshotResponse<>(latestSnapshotTime, items);
    }

    /** 활성 종목 전체 반환 — 관심종목 등록 화면 자동완성용 */
    public List<StockResponse> getAllStocks() {
        return stockInfoRepository.findByActiveTrue().stream()
                .map(s -> new StockResponse(s.getStockCode(), s.getStockName(), s.getMarketType()))
                .toList();
    }
}
