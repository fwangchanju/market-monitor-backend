package dev.eolmae.marketmonitor.collector;

import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.domain.history.ShortSellingDailyHistory;
import dev.eolmae.marketmonitor.domain.history.repository.ShortSellingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.WatchStockCacheService;
import dev.eolmae.marketmonitor.external.kiwoom.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Ka10014Request;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Ka10014Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ka10014: 공매도추이요청
// ka10014는 장 종료 후 확정되는 일별 데이터만 제공 — 장중 실시간 없음
// 스케줄러: 19:00 1회, strt_dt=today → short_selling_daily 적재
// 백필: strt_dt=today-60 → short_selling_daily 전체 적재
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortSellingCollector {

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final String TM_TP_DAILY = "2";

	private final KiwoomApiClient kiwoomApiClient;
	private final ShortSellingDailyHistoryRepository dailyRepository;
	private final WatchStockCacheService watchStockCacheService;

	/** 스케줄러 호출 — 당일 확정 데이터 적재 (19:00 이후) */
	@Transactional
	public void collect(LocalDateTime snapshotTime) {
		String todayStr = snapshotTime.toLocalDate().format(DATE_FMT);
		List<String> stockCodes = watchStockCacheService.findDistinctStockCodes();
		for (String stockCode : stockCodes) {
			try {
				collectForStock(stockCode, todayStr, todayStr);
			} catch (Exception e) {
				log.error("공매도 수집 실패: stockCode={}", stockCode, e);
			}
		}
	}

	/** 관심종목 신규 등록 시 백필 — today-60일부터 수집, 비동기 호출 */
	@Transactional
	public void backfill(String stockCode, LocalDateTime snapshotTime) {
		LocalDate today = snapshotTime.toLocalDate();
		String endDt = today.format(DATE_FMT);
		String startDt = today.minusDays(60).format(DATE_FMT);
		collectForStock(stockCode, startDt, endDt);
		log.info("공매도 백필 완료: stockCode={}", stockCode);
	}

	private void collectForStock(String stockCode, String startDt, String endDt) {
		var request = new Ka10014Request(stockCode, TM_TP_DAILY, startDt, endDt);
		Ka10014Response response = kiwoomApiClient.post(request, Ka10014Response.class);

		if (response.ticks() == null || response.ticks().isEmpty()) {
			log.debug("공매도 데이터 없음: stockCode={}", stockCode);
			return;
		}

		for (Ka10014Response.ShortTick tick : response.ticks()) {
			if (tick.dt() == null || tick.dt().isBlank()) continue;
			LocalDate tradeDate = parseDate(tick.dt());
			if (tradeDate == null) continue;

			if (dailyRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) continue;

			BigDecimal closePrice = NumberParser.parseBigDecimal(tick.closePric());
			BigDecimal priceChange = NumberParser.parseBigDecimal(tick.predPre());
			BigDecimal changeRate = NumberParser.parseBigDecimal(tick.fluRt());
			long tradingVolume = NumberParser.parseLong(tick.trdeQty());
			long shortVolume = NumberParser.parseLong(tick.shrtsQty());
			long cumulativeShortVolume = NumberParser.parseLong(tick.ovrShrtsQty());
			BigDecimal shortRatio = NumberParser.parseBigDecimal(tick.trdeWght());
			BigDecimal shortAmount = NumberParser.parseBigDecimal(tick.shrtsTrdePrica());
			BigDecimal shortAvgPrice = NumberParser.parseBigDecimal(tick.shrtsAvgPric());

			dailyRepository.save(ShortSellingDailyHistory.create(
				stockCode, tradeDate,
				closePrice, priceChange, changeRate,
				tradingVolume, shortVolume, cumulativeShortVolume,
				shortRatio, shortAmount, shortAvgPrice));
		}

		log.debug("공매도 수집 완료: stockCode={}", stockCode);
	}

	private static LocalDate parseDate(String dt) {
		try {
			return LocalDate.parse(dt.trim(), DATE_FMT);
		} catch (Exception e) {
			return null;
		}
	}
}
