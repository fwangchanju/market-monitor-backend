package dev.eolmae.marketmonitor.domain.dashboard.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.dashboard.dto.DashboardResponse;
import dev.eolmae.marketmonitor.domain.dashboard.dto.IndexContributionItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.IntradayInvestorRankingItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.IntradayInvestorSummaryItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.InvestorTradingSummaryItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.MarketOverviewItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.ProgramTradingDailyHistoryItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.ProgramTradingHistoryItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.ProgramTradingRankingItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.ShortSellingHistoryItem;
import dev.eolmae.marketmonitor.domain.dashboard.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.dashboard.dto.StockHistoryResponse;
import dev.eolmae.marketmonitor.domain.dashboard.dto.StockResponse;
import dev.eolmae.marketmonitor.domain.dashboard.dto.WatchStockResponse;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

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

    public DashboardQueryService(
            MarketOverviewSnapshotRepository marketOverviewSnapshotRepository,
            InvestorTradingSummarySnapshotRepository investorTradingSummarySnapshotRepository,
            IntradayInvestorRankingSnapshotRepository intradayInvestorRankingSnapshotRepository,
            ProgramTradingRankingSnapshotRepository programTradingRankingSnapshotRepository,
            IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository,
            StockInfoRepository stockInfoRepository,
            ProgramTradingHistoryRepository programTradingHistoryRepository,
            ProgramTradingDailyHistoryRepository programTradingDailyHistoryRepository,
            ShortSellingDailyHistoryRepository shortSellingDailyHistoryRepository,
            WatchStockCacheService watchStockCacheService,
            StockInfoCacheService stockInfoCacheService) {
        this.marketOverviewSnapshotRepository = marketOverviewSnapshotRepository;
        this.investorTradingSummarySnapshotRepository = investorTradingSummarySnapshotRepository;
        this.intradayInvestorRankingSnapshotRepository = intradayInvestorRankingSnapshotRepository;
        this.programTradingRankingSnapshotRepository = programTradingRankingSnapshotRepository;
        this.indexContributionRankingSnapshotRepository = indexContributionRankingSnapshotRepository;
        this.stockInfoRepository = stockInfoRepository;
        this.programTradingHistoryRepository = programTradingHistoryRepository;
        this.programTradingDailyHistoryRepository = programTradingDailyHistoryRepository;
        this.shortSellingDailyHistoryRepository = shortSellingDailyHistoryRepository;
        this.watchStockCacheService = watchStockCacheService;
        this.stockInfoCacheService = stockInfoCacheService;
    } // TODO 이거 왜 RequiredArgs.. 로 안하고 직접 명시했지

    public DashboardResponse getDashboard() {
        var snapshotTime =
                marketOverviewSnapshotRepository.findLatestSnapshotTime().orElse(null);

        // 아직 수집된 데이터가 없으면 빈 응답 반환
        if (snapshotTime == null) {
            return new DashboardResponse(
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
                .findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
                        snapshotTime, Market.KOSPI, IntradayInvestorType.FOREIGNER, IntradayRankingType.NET_BUY)
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
        var programItems = getProgramTradingRankings(Market.KOSPI, ProgramRankingType.NET_BUY, AmtQtyType.AMOUNT)
                .items();

        // 대시보드 요약: KOSPI 지수 기여도 상위
        var indexContributionItems =
                indexContributionRankingSnapshotRepository
                        .findBySnapshotTimeAndMarketTypeOrderByRankAsc(snapshotTime, Market.KOSPI)
                        .stream()
                        .map(item -> new IndexContributionItem(
                                item.getMarketType(),
                                item.getRank(),
                                item.getStockCode(),
                                item.getStockName(),
                                item.getContributionScore(),
                                item.getPriceChangeRate()))
                        .toList();

        return new DashboardResponse(
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

    public SnapshotResponse<IntradayInvestorRankingItem> getIntradayRankings(
            Market marketType, IntradayInvestorType investorType, IntradayRankingType rankingType) {
        var snapshotTime = intradayInvestorRankingSnapshotRepository
                .findLatestSnapshotTime()
                .orElse(null);
        if (snapshotTime == null) {
            return new SnapshotResponse<>(null, List.of());
        }

        var items = intradayInvestorRankingSnapshotRepository
                .findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
                        snapshotTime, marketType, investorType, rankingType)
                .stream()
                .limit(10)
                .map(item -> new IntradayInvestorRankingItem(
                        item.getMarketType(),
                        item.getInvestorType(),
                        item.getRank(),
                        item.getStockCode(),
                        item.getStockName(),
                        item.getNetBuyAmount(),
                        item.getTradedVolume()))
                .toList();

        return new SnapshotResponse<>(snapshotTime, items);
    }

    public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            Market marketType, ProgramRankingType rankingType, AmtQtyType amtQtyType) {
        var snapshotTime =
                programTradingRankingSnapshotRepository.findLatestSnapshotTime().orElse(null);
        if (snapshotTime == null) {
            return new SnapshotResponse<>(null, List.of());
        }

        List<ProgramTradingRankingSnapshot> snapshots;
        if (marketType == null) {
            // 전체: KOSPI + KOSDAQ 합산 후 netBuyAmount 기준 재정렬
            var kospi =
                    programTradingRankingSnapshotRepository
                            .findBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
                                    snapshotTime, Market.KOSPI, rankingType, amtQtyType);
            var kosdaq =
                    programTradingRankingSnapshotRepository
                            .findBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
                                    snapshotTime, Market.KOSDAQ, rankingType, amtQtyType);
            snapshots = Stream.concat(kospi.stream(), kosdaq.stream())
                    .sorted(Comparator.comparing(ProgramTradingRankingSnapshot::getProgramNetBuyAmount)
                            .reversed())
                    .toList();
        } else {
            snapshots =
                    programTradingRankingSnapshotRepository
                            .findBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
                                    snapshotTime, marketType, rankingType, amtQtyType);
        }

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

    public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistory(
            String stockCode, LocalDateTime from, LocalDateTime to) {
        var items =
                programTradingHistoryRepository
                        .findByStockCodeAndSnapshotTimeBetweenOrderBySnapshotTimeAsc(stockCode, from, to)
                        .stream()
                        .map(item -> new ProgramTradingHistoryItem(
                                item.getSnapshotTime(),
                                item.getProgramBuyAmount(),
                                item.getProgramSellAmount(),
                                item.getProgramNetBuyAmount()))
                        .toList();

        return new StockHistoryResponse<>(stockCode, items);
    }

    public StockHistoryResponse<ProgramTradingDailyHistoryItem> getProgramTradingDailyHistory(
            String stockCode, LocalDate from, LocalDate to) {
        var items =
                programTradingDailyHistoryRepository
                        .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, from, to)
                        .stream()
                        .map(item -> new ProgramTradingDailyHistoryItem(
                                item.getTradeDate(),
                                item.getProgramBuyAmount(),
                                item.getProgramSellAmount(),
                                item.getProgramNetBuyAmount()))
                        .toList();

        return new StockHistoryResponse<>(stockCode, items);
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
     *   <li>{@code market 미지정}: KOSPI+KOSDAQ 스냅샷 종목코드 기준 합산 후 재정렬</li>
     *   <li>{@code investor=FOREIGN_TOTAL}: FOREIGNER+FOREIGN_COMPANY 종목코드 기준 합산 후 재정렬</li>
     *   <li>{@code ranking=NET_SELL}: netBuyAmount 절댓값 변환 후 반환</li>
     * </ul>
     */
    public SnapshotResponse<IntradayInvestorSummaryItem> getIntradayTop(
            Market market, IntradayInvestorType investor, IntradayRankingType ranking) {
        var snapshotTime = intradayInvestorRankingSnapshotRepository
                .findLatestSnapshotTime()
                .orElse(null);
        if (snapshotTime == null) {
            return new SnapshotResponse<>(null, List.of());
        }

        List<Market> markets = market == null ? List.of(Market.KOSPI, Market.KOSDAQ) : List.of(market);

        List<IntradayInvestorType> investors = investor == IntradayInvestorType.FOREIGN_TOTAL
                ? List.of(IntradayInvestorType.FOREIGNER, IntradayInvestorType.FOREIGN_COMPANY)
                : List.of(investor);

        // market·investor를 IN으로 한 번에 조회 → stockCode 기준 합산
        Map<String, BigDecimal> netByStock = new HashMap<>();
        Map<String, String> nameByStock = new HashMap<>();

        intradayInvestorRankingSnapshotRepository
                .findBySnapshotTimeAndMarketTypeInAndInvestorTypeInAndRankingTypeOrderByRankAsc(
                        snapshotTime, markets, investors, ranking)
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
