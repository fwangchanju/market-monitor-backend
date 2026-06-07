package dev.eolmae.marketmonitor.collector;

import dev.eolmae.marketmonitor.common.enums.Board;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.domain.dashboard.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.dashboard.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.external.kiwoom.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Ka20001Request;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Ka20001Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketOverviewCollector {

	private final KiwoomApiClient kiwoomApiClient;
	private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;

	@Transactional
	public void collect(LocalDateTime snapshotTime) {
		for (Board board : Board.values()) {
			try {
				collectForMarket(board, snapshotTime);
			} catch (Exception e) {
				log.error("시장종합 수집 실패: board={}", board, e);
			}
		}
	}

	private void collectForMarket(Board board, LocalDateTime snapshotTime) {
		Exchange marketType = Exchange.valueOf(board.name());
		String mrktTp = switch (board) {
			case KOSPI -> MrktTp.KOSPI.value;
			case KOSDAQ -> MrktTp.KOSDAQ.value;
		};
		String indsCd = switch (board) {
			case KOSPI -> IndsCd.KOSPI.value;
			case KOSDAQ -> IndsCd.KOSDAQ.value;
		};
		var request = new Ka20001Request(mrktTp, indsCd);
		var response = kiwoomApiClient.post(request, Ka20001Response.class);

		LocalDateTime now = LocalDateTime.now();

		BigDecimal indexValue = NumberParser.parseBigDecimal(response.curPrc()).abs();
		BigDecimal changeValue = NumberParser.parseBigDecimal(response.predPre());
		BigDecimal changeRate = NumberParser.parseBigDecimal(response.fluRt());
		BigDecimal tradingValue = NumberParser.parseBigDecimal(response.trdePrica());
		String marketStatus = response.mrktStatClsCode() != null ? response.mrktStatClsCode().trim() : "";
		int upperLimitCount = NumberParser.parseInt(response.upl());
		int lowerLimitCount = NumberParser.parseInt(response.lst());
		int advancers = NumberParser.parseInt(response.rising());
		int decliners = NumberParser.parseInt(response.fall());
		int unchangedCount = NumberParser.parseInt(response.stdns());

		if (marketOverviewSnapshotRepository.findByMarketTypeAndSnapshotTime(marketType, snapshotTime).isEmpty()) {
			marketOverviewSnapshotRepository.save(MarketOverviewSnapshot.create(
				marketType, snapshotTime, now, marketStatus, indexValue, changeValue, changeRate,
				tradingValue, upperLimitCount, lowerLimitCount, advancers, decliners, unchangedCount));
		}

		log.debug("시장종합 수집 완료: market={}, index={}", marketType, indexValue);
	}

	private enum MrktTp {
		KOSPI("0"), KOSDAQ("1");  // ka20001 mrkt_tp
		final String value;
		MrktTp(String value) { this.value = value; }
	}

	private enum IndsCd {
		KOSPI("001"), KOSDAQ("101");  // ka20001 inds_cd
		final String value;
		IndsCd(String value) { this.value = value; }
	}
}
