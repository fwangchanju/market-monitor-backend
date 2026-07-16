package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.util.DateParser;
import dev.eolmae.marketmonitor.common.util.Strings;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.DailyProgramTradeTrendRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.DailyProgramTradeTrendResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingDailyHistory;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockCacheService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ka90013: 종목일별프로그램매매추이
// 각 틱은 해당 날짜의 일별 합계 → KRX[date] + NXT[date] 합산 저장
// 순매수금액은 prm_netprps_amt 미사용 (-- 파싱 오류) → buy - sell 직접 계산
// 날짜 파라미터가 없는 API라 매번 서버가 주는 범위를 그대로 받아 이미 없는 날짜만 저장
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramTradeDailyCollector {

    private final KiwoomApiClient kiwoomApiClient;
    private final ProgramTradingDailyHistoryRepository dailyHistoryRepository;
    private final WatchStockCacheService watchStockCacheService;

    /** 스케줄러 호출 */
    @Transactional
    public void collect() {
        List<WatchStock> watchStocks = watchStockCacheService.getCache();
        for (WatchStock watchStock : watchStocks) {
            try {
                collectForStock(watchStock);
            } catch (Exception e) {
                log.error("프로그램매매 일별이력 수집 실패: stockCode={}", watchStock.getStockCode(), e);
            }
        }
    }

    /** 관심종목 신규 등록 시 백필 */
    @Transactional
    public void backfill(WatchStock watchStock) {
        collectForStock(watchStock);
    }

    private void collectForStock(WatchStock watchStock) {
        String stockCode = watchStock.getStockCode();
        String stexTypeCode = StexType.KRX.code();
        var krxRequest = new DailyProgramTradeTrendRequest(stockCode, stexTypeCode);
        DailyProgramTradeTrendResponse krxResponse =
                kiwoomApiClient.post(krxRequest, DailyProgramTradeTrendResponse.class);

        var nxtRequest = new DailyProgramTradeTrendRequest(stockCode + "_NX", stexTypeCode);
        DailyProgramTradeTrendResponse nxtResponse =
                kiwoomApiClient.post(nxtRequest, DailyProgramTradeTrendResponse.class);

        List<DailyProgramTradeTrendResponse.DailyTick> krxTicks =
                krxResponse.ticks() != null ? krxResponse.ticks() : List.of();
        List<DailyProgramTradeTrendResponse.DailyTick> nxtTicks =
                nxtResponse.ticks() != null ? nxtResponse.ticks() : List.of();

        Map<String, TradeAmount> merged = new HashMap<>();

        for (DailyProgramTradeTrendResponse.DailyTick tick : krxTicks) {
            String dt = Strings.trimToEmpty(tick.dt());
            if (dt.isBlank()) {
                continue;
            }
            accumulateDaily(merged, dt, tick.prmBuyAmt(), tick.prmSellAmt());
        }
        for (DailyProgramTradeTrendResponse.DailyTick tick : nxtTicks) {
            String dt = Strings.trimToEmpty(tick.dt());
            if (dt.isBlank()) {
                continue;
            }
            accumulateDaily(merged, dt, tick.prmBuyAmt(), tick.prmSellAmt());
        }

        for (Map.Entry<String, TradeAmount> entry : merged.entrySet()) {
            LocalDate date = DateParser.parseDate(entry.getKey());
            if (date == null) {
                continue;
            }
            if (dailyHistoryRepository.existsByStockCodeAndTradeDate(stockCode, date)) {
                continue;
            }
            dailyHistoryRepository.save(toEntity(stockCode, date, entry.getValue()));
        }

        log.debug("프로그램매매 일별이력 수집 완료: stockCode={}", stockCode);
    }

    private static void accumulateDaily(Map<String, TradeAmount> merged, String dt, String buyAmt, String sellAmt) {
        merged.merge(
                dt,
                new TradeAmount(KiwoomValueParser.parseBigDecimal(buyAmt), KiwoomValueParser.parseBigDecimal(sellAmt)),
                (a, b) -> a.add(b.buy(), b.sell()));
    }

    private static ProgramTradingDailyHistory toEntity(String stockCode, LocalDate date, TradeAmount amt) {
        return ProgramTradingDailyHistory.create(stockCode, date, amt.buy(), amt.sell(), amt.net());
    }

    private record TradeAmount(BigDecimal buy, BigDecimal sell) {
        TradeAmount add(BigDecimal b, BigDecimal s) {
            return new TradeAmount(buy.add(b), sell.add(s));
        }

        BigDecimal net() {
            return buy.subtract(sell);
        }
    }
}
