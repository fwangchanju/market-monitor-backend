package dev.eolmae.marketmonitor.api.service;

import dev.eolmae.marketmonitor.api.dto.DashboardResponse;
import dev.eolmae.marketmonitor.api.dto.IndexContributionItem;
import dev.eolmae.marketmonitor.api.dto.IntradayInvestorRankingItem;
import dev.eolmae.marketmonitor.api.dto.IntradayInvestorTopItem;
import dev.eolmae.marketmonitor.api.dto.InvestorTradingSummaryItem;
import dev.eolmae.marketmonitor.api.dto.MarketOverviewItem;
import dev.eolmae.marketmonitor.api.dto.NotificationSettingResponse;
import dev.eolmae.marketmonitor.api.dto.ProgramTradingDailyHistoryItem;
import dev.eolmae.marketmonitor.api.dto.ProgramTradingHistoryItem;
import dev.eolmae.marketmonitor.api.dto.ProgramTradingRankingItem;
import dev.eolmae.marketmonitor.api.dto.ShortSellingHistoryItem;
import dev.eolmae.marketmonitor.api.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.api.dto.StockHistoryResponse;
import dev.eolmae.marketmonitor.api.dto.StockMasterItem;
import dev.eolmae.marketmonitor.api.dto.WatchStockItem;
import dev.eolmae.marketmonitor.common.enums.AmtQtyType;
import dev.eolmae.marketmonitor.common.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.common.enums.IntradayRankingType;
import dev.eolmae.marketmonitor.common.enums.Board;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.ProgramRankingType;
import dev.eolmae.marketmonitor.domain.dashboard.IntradayInvestorRankingSnapshot;
import dev.eolmae.marketmonitor.domain.dashboard.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.dashboard.ProgramTradingRankingSnapshot;
import dev.eolmae.marketmonitor.domain.dashboard.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.dashboard.repository.IntradayInvestorRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.dashboard.repository.InvestorTradingSummarySnapshotRepository;
import dev.eolmae.marketmonitor.domain.dashboard.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.dashboard.repository.ProgramTradingRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.history.repository.ProgramTradingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.history.repository.ProgramTradingHistoryRepository;
import dev.eolmae.marketmonitor.domain.history.repository.ShortSellingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.StockMasterRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.WatchStockRepository;
import dev.eolmae.marketmonitor.domain.user.repository.UserNotificationSettingRepository;
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

	private static final String DEFAULT_USER_KEY = "default";

	private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;
	private final InvestorTradingSummarySnapshotRepository investorTradingSummarySnapshotRepository;
	private final IntradayInvestorRankingSnapshotRepository intradayInvestorRankingSnapshotRepository;
	private final ProgramTradingRankingSnapshotRepository programTradingRankingSnapshotRepository;
	private final IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository;
	private final StockMasterRepository stockMasterRepository;
	private final WatchStockRepository watchStockRepository;
	private final UserNotificationSettingRepository userNotificationSettingRepository;
	private final ProgramTradingHistoryRepository programTradingHistoryRepository;
	private final ProgramTradingDailyHistoryRepository programTradingDailyHistoryRepository;
	private final ShortSellingDailyHistoryRepository shortSellingDailyHistoryRepository;

	public DashboardQueryService(
		MarketOverviewSnapshotRepository marketOverviewSnapshotRepository,
		InvestorTradingSummarySnapshotRepository investorTradingSummarySnapshotRepository,
		IntradayInvestorRankingSnapshotRepository intradayInvestorRankingSnapshotRepository,
		ProgramTradingRankingSnapshotRepository programTradingRankingSnapshotRepository,
		IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository,
		StockMasterRepository stockMasterRepository,
		WatchStockRepository watchStockRepository,
		UserNotificationSettingRepository userNotificationSettingRepository,
		ProgramTradingHistoryRepository programTradingHistoryRepository,
		ProgramTradingDailyHistoryRepository programTradingDailyHistoryRepository,
		ShortSellingDailyHistoryRepository shortSellingDailyHistoryRepository
	) {
		this.marketOverviewSnapshotRepository = marketOverviewSnapshotRepository;
		this.investorTradingSummarySnapshotRepository = investorTradingSummarySnapshotRepository;
		this.intradayInvestorRankingSnapshotRepository = intradayInvestorRankingSnapshotRepository;
		this.programTradingRankingSnapshotRepository = programTradingRankingSnapshotRepository;
		this.indexContributionRankingSnapshotRepository = indexContributionRankingSnapshotRepository;
		this.stockMasterRepository = stockMasterRepository;
		this.watchStockRepository = watchStockRepository;
		this.userNotificationSettingRepository = userNotificationSettingRepository;
		this.programTradingHistoryRepository = programTradingHistoryRepository;
		this.programTradingDailyHistoryRepository = programTradingDailyHistoryRepository;
		this.shortSellingDailyHistoryRepository = shortSellingDailyHistoryRepository;
	}

	public DashboardResponse getDashboard() {
		var snapshotTime = marketOverviewSnapshotRepository.findLatestSnapshotTime().orElse(null);

		// 아직 수집된 데이터가 없으면 빈 응답 반환
		if (snapshotTime == null) {
			return new DashboardResponse(
				null, null, null,
				List.of(), List.of(), List.of(), List.of(), List.of(),
				getWatchStocks(), getNotificationSetting()
			);
		}

		var marketOverviews = marketOverviewSnapshotRepository
			.findBySnapshotTimeOrderByMarketTypeAsc(snapshotTime);

		var investorSummaries = investorTradingSummarySnapshotRepository
			.findBySnapshotTimeOrderByMarketTypeAscInvestorTypeAsc(snapshotTime);

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
				snapshotTime, Exchange.KOSPI, IntradayInvestorType.FOREIGNER, IntradayRankingType.NET_BUY)
			.stream().map(item -> new IntradayInvestorRankingItem(
				item.getMarketType(), item.getInvestorType(), item.getRank(),
				item.getStockCode(), item.getStockName(),
				item.getNetBuyAmount(), item.getTradedVolume()
			)).toList();

		// 대시보드 요약: 프로그램 순매수 상위 (KOSPI 기준 금액)
		var programItems = getProgramTradingRankings(Exchange.KOSPI, ProgramRankingType.NET_BUY, AmtQtyType.AMOUNT).items();

		// 대시보드 요약: KOSPI 지수 기여도 상위
		var indexContributionItems = indexContributionRankingSnapshotRepository
			.findBySnapshotTimeAndMarketTypeOrderByRankAsc(snapshotTime, Exchange.KOSPI)
			.stream().map(item -> new IndexContributionItem(
				item.getMarketType(), item.getRank(), item.getStockCode(), item.getStockName(),
				item.getContributionScore(), item.getPriceChangeRate()
			)).toList();

		return new DashboardResponse(
			snapshotTime,
			lastCollectedAt,
			marketStatus,
			marketOverviews.stream().map(item -> new MarketOverviewItem(
				item.getMarketType(), item.getMarketStatus(),
				item.getIndexValue(), item.getChangeValue(), item.getChangeRate(),
				item.getTradingValue(), item.getUpperLimitCount(), item.getLowerLimitCount(),
				item.getAdvancers(), item.getDecliners(), item.getUnchangedCount()
			)).toList(),
			investorSummaries.stream().map(item -> new InvestorTradingSummaryItem(
				item.getMarketType(), item.getInvestorType(),
				item.getBuyAmount(), item.getSellAmount(), item.getNetBuyAmount()
			)).toList(),
			intradayItems,
			programItems,
			indexContributionItems,
			getWatchStocks(),
			getNotificationSetting()
		);
	}

	public SnapshotResponse<IntradayInvestorRankingItem> getIntradayRankings(
		Exchange marketType, IntradayInvestorType investorType, IntradayRankingType rankingType
	) {
		var snapshotTime = intradayInvestorRankingSnapshotRepository.findLatestSnapshotTime()
			.orElse(null);
		if (snapshotTime == null) {
			return new SnapshotResponse<>(null, List.of());
		}

		var items = intradayInvestorRankingSnapshotRepository
			.findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
				snapshotTime, marketType, investorType, rankingType)
			.stream().limit(10).map(item -> new IntradayInvestorRankingItem(
				item.getMarketType(), item.getInvestorType(), item.getRank(),
				item.getStockCode(), item.getStockName(),
				item.getNetBuyAmount(), item.getTradedVolume()
			)).toList();

		return new SnapshotResponse<>(snapshotTime, items);
	}

	public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
		Exchange marketType, ProgramRankingType rankingType, AmtQtyType amtQtyType
	) {
		var snapshotTime = programTradingRankingSnapshotRepository.findLatestSnapshotTime()
			.orElse(null);
		if (snapshotTime == null) {
			return new SnapshotResponse<>(null, List.of());
		}

		List<ProgramTradingRankingSnapshot> snapshots;
		if (marketType == null) {
			// 전체: KOSPI + KOSDAQ 합산 후 netBuyAmount 기준 재정렬
			var kospi = programTradingRankingSnapshotRepository
				.findBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
					snapshotTime, Exchange.KOSPI, rankingType, amtQtyType);
			var kosdaq = programTradingRankingSnapshotRepository
				.findBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
					snapshotTime, Exchange.KOSDAQ, rankingType, amtQtyType);
			snapshots = Stream.concat(kospi.stream(), kosdaq.stream())
				.sorted(Comparator.comparing(ProgramTradingRankingSnapshot::getProgramNetBuyAmount).reversed())
				.toList();
		} else {
			snapshots = programTradingRankingSnapshotRepository
				.findBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyTypeOrderByRankAsc(
					snapshotTime, marketType, rankingType, amtQtyType);
		}

		return new SnapshotResponse<>(snapshotTime, snapshots.stream()
			.map(item -> new ProgramTradingRankingItem(
				item.getRank(), item.getStockCode(), item.getStockName(),
				item.getProgramBuyAmount(), item.getProgramSellAmount(), item.getProgramNetBuyAmount()
			)).toList());
	}

	public SnapshotResponse<IndexContributionItem> getIndexContribution(Exchange marketType) {
		var snapshotTime = indexContributionRankingSnapshotRepository.findLatestSnapshotTime()
			.orElse(null);
		if (snapshotTime == null) {
			return new SnapshotResponse<>(null, List.of());
		}

		var items = indexContributionRankingSnapshotRepository
			.findBySnapshotTimeAndMarketTypeOrderByRankAsc(snapshotTime, marketType)
			.stream().map(item -> new IndexContributionItem(
				item.getMarketType(), item.getRank(), item.getStockCode(), item.getStockName(),
				item.getContributionScore(), item.getPriceChangeRate()
			)).toList();

		return new SnapshotResponse<>(snapshotTime, items);
	}

	public NotificationSettingResponse getNotificationSetting() {
		return userNotificationSettingRepository.findByUserUserKey(DEFAULT_USER_KEY)
			.map(setting -> new NotificationSettingResponse(
				setting.getUser().getUserKey(),
				setting.isReminderEnabled(),
				setting.getReminderTime(),
				setting.getTimezone()
			))
			.orElse(null);
	}

	public List<WatchStockItem> getWatchStocks() {
		return watchStockRepository.findByUserUserKey(DEFAULT_USER_KEY).stream()
			.map(item -> new WatchStockItem(
				item.getStock().getStockCode(),
				item.getStock().getStockName(),
				item.getStock().getMarketType(),
				item.isPrimary()
			))
			.toList();
	}

	public StockHistoryResponse<ProgramTradingHistoryItem> getProgramTradingHistory(
		String stockCode, LocalDateTime from, LocalDateTime to
	) {
		var items = programTradingHistoryRepository
			.findByStockCodeAndSnapshotTimeBetweenOrderBySnapshotTimeAsc(stockCode, from, to)
			.stream().map(item -> new ProgramTradingHistoryItem(
				item.getSnapshotTime(),
				item.getProgramBuyAmount(),
				item.getProgramSellAmount(),
				item.getProgramNetBuyAmount()
			)).toList();

		return new StockHistoryResponse<>(stockCode, items);
	}

	public StockHistoryResponse<ProgramTradingDailyHistoryItem> getProgramTradingDailyHistory(
		String stockCode, LocalDate from, LocalDate to
	) {
		var items = programTradingDailyHistoryRepository
			.findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, from, to)
			.stream().map(item -> new ProgramTradingDailyHistoryItem(
				item.getTradeDate(),
				item.getProgramBuyAmount(),
				item.getProgramSellAmount(),
				item.getProgramNetBuyAmount()
			)).toList();

		return new StockHistoryResponse<>(stockCode, items);
	}

	/** 공매도 추이 최신 10건 반환 */
	public StockHistoryResponse<ShortSellingHistoryItem> getShortSellingHistory(String stockCode) {
		var items = shortSellingDailyHistoryRepository
			.findByStockCodeOrderByTradeDateDesc(stockCode)
			.stream().limit(10)
			.map(item -> new ShortSellingHistoryItem(
				item.getTradeDate(), null,
				item.getClosePrice(), item.getPriceChange(), item.getChangeRate(),
				item.getTradingVolume(), item.getShortVolume(), item.getCumulativeShortVolume(),
				item.getShortRatio(), item.getShortAmount(), item.getShortAvgPrice()
			)).toList();

		return new StockHistoryResponse<>(stockCode, items);
	}

	/**
	 * 장중 투자자별 매매 상위 top 10 반환.
	 * <ul>
	 *   <li>{@code market=ALL}: KOSPI+KOSDAQ 스냅샷 종목코드 기준 합산 후 재정렬</li>
	 *   <li>{@code investor=FOREIGN_TOTAL}: FOREIGNER+FOREIGN_COMPANY 종목코드 기준 합산 후 재정렬</li>
	 *   <li>{@code ranking=NET_SELL}: netBuyAmount 절댓값 변환 후 반환</li>
	 * </ul>
	 */
	public SnapshotResponse<IntradayInvestorTopItem> getIntradayTop(
		Exchange market, IntradayInvestorType investor, IntradayRankingType ranking
	) {
		var snapshotTime = intradayInvestorRankingSnapshotRepository.findLatestSnapshotTime().orElse(null);
		if (snapshotTime == null) {
			return new SnapshotResponse<>(null, List.of());
		}

		List<Exchange> markets = (market == Exchange.ALL)
			? List.of(Exchange.KOSPI, Exchange.KOSDAQ)
			: List.of(market);

		List<IntradayInvestorType> investors = (investor == IntradayInvestorType.FOREIGN_TOTAL)
			? List.of(IntradayInvestorType.FOREIGNER, IntradayInvestorType.FOREIGN_COMPANY)
			: List.of(investor);

		// 모든 조합 조회 → stockCode 기준 합산
		Map<String, BigDecimal> netByStock = new HashMap<>();
		Map<String, String> nameByStock = new HashMap<>();

		for (Exchange m : markets) {
			for (IntradayInvestorType inv : investors) {
				intradayInvestorRankingSnapshotRepository
					.findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
						snapshotTime, m, inv, ranking)
					.forEach(s -> {
						netByStock.merge(s.getStockCode(), s.getNetBuyAmount(), BigDecimal::add);
						nameByStock.putIfAbsent(s.getStockCode(), s.getStockName());
					});
			}
		}

		boolean isNetSell = ranking == IntradayRankingType.NET_SELL;

		// 순매수: netBuyAmount 내림차순 / 순매도: netBuyAmount 오름차순(가장 많이 매도한 순) → 절댓값 반환
		Comparator<Map.Entry<String, BigDecimal>> comparator = isNetSell
			? Map.Entry.comparingByValue()          // 가장 작은(음수) 순
			: Map.Entry.<String, BigDecimal>comparingByValue().reversed();

		List<IntradayInvestorTopItem> items = netByStock.entrySet().stream()
			.sorted(comparator)
			.limit(10)
			.map(e -> new IntradayInvestorTopItem(
				e.getKey(),
				nameByStock.get(e.getKey()),
				isNetSell ? e.getValue().abs() : e.getValue()
			))
			.toList();

		return new SnapshotResponse<>(snapshotTime, items);
	}

	/** 활성 종목 전체 반환 — 관심종목 등록 화면 자동완성용 */
	public List<StockMasterItem> getActiveStocks() {
		return stockMasterRepository.findByActiveTrueOrderByStockCodeAsc().stream()
			.map(s -> new StockMasterItem(s.getStockCode(), s.getStockName(), s.getMarketType()))
			.toList();
	}
}
