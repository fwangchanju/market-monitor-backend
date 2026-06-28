package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.util.DateParser;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ka90013: 종목일별프로그램매매추이
// 각 틱은 해당 날짜의 일별 합계 → KRX[date] + NXT[date] 합산 저장
// 순매수금액은 prm_netprps_amt 미사용 (-- 파싱 오류) → buy - sell 직접 계산
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramTradeDailyCollector {

    private final KiwoomApiClient kiwoomApiClient;
    private final ProgramTradingDailyHistoryRepository dailyHistoryRepository;
    private final WatchStockCacheService watchStockCacheService;

    /** 스케줄러 호출 — 당일 일별 데이터만 적재 */
    @Transactional
    public void collect(LocalDate tradeDate) {
        List<WatchStock> watchStocks = watchStockCacheService.getCache();
        for (WatchStock watchStock : watchStocks) {
            try {
                collectForStock(watchStock, tradeDate, true);
            } catch (Exception e) {
                log.error("프로그램매매 일별이력 수집 실패: stockCode={}", watchStock.getStockCode(), e);
            }
        }
    }

    @Transactional
    public void backfill(WatchStock watchStock) {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        collectForStock(watchStock, today, false);
    }

    private void collectForStock(WatchStock watchStock, LocalDate targetDate, boolean todayOnly) {
        String stockCode = watchStock.getStockCode();
        if (todayOnly && dailyHistoryRepository.existsByStockCodeAndTradeDate(stockCode, targetDate)) {
            log.debug("프로그램매매 일별이력 이미 존재, 스킵: stockCode={}, tradeDate={}", stockCode, targetDate);
            return;
        }
        var krxRequest = new DailyProgramTradeTrendRequest(stockCode, StexType.KRX.code());
        DailyProgramTradeTrendResponse krxResponse =
                kiwoomApiClient.post(krxRequest, DailyProgramTradeTrendResponse.class);

        var nxtRequest = new DailyProgramTradeTrendRequest(stockCode + "_NX", StexType.KRX.code());
        DailyProgramTradeTrendResponse nxtResponse =
                kiwoomApiClient.post(nxtRequest, DailyProgramTradeTrendResponse.class);

        List<DailyProgramTradeTrendResponse.DailyTick> krxTicks =
                krxResponse.ticks() != null ? krxResponse.ticks() : List.of();
        List<DailyProgramTradeTrendResponse.DailyTick> nxtTicks =
                nxtResponse.ticks() != null ? nxtResponse.ticks() : List.of();

        Map<String, TradeAmount> merged = new HashMap<>();

        for (DailyProgramTradeTrendResponse.DailyTick tick : krxTicks) {
            String dt = StringUtils.trim(tick.dt());
            if (StringUtils.isBlank(dt)) {
                continue;
            }
            accumulateDaily(merged, dt, tick.prmBuyAmt(), tick.prmSellAmt());
        }
        for (DailyProgramTradeTrendResponse.DailyTick tick : nxtTicks) {
            String dt = StringUtils.trim(tick.dt());
            if (StringUtils.isBlank(dt)) {
                continue;
            }
            accumulateDaily(merged, dt, tick.prmBuyAmt(), tick.prmSellAmt());
        }

        for (Map.Entry<String, TradeAmount> entry : merged.entrySet()) {
            LocalDate date = DateParser.parseDate(entry.getKey());
            if (date == null) {
                continue;
            }
            if (todayOnly && !date.equals(targetDate)) {
                continue;
            }
            if (!todayOnly && dailyHistoryRepository.existsByStockCodeAndTradeDate(stockCode, date)) {
                continue;
            }
            TradeAmount amt = entry.getValue();
            dailyHistoryRepository.save(
                    ProgramTradingDailyHistory.create(stockCode, date, amt.buy(), amt.sell(), amt.net()));
        }

        log.debug("프로그램매매 일별이력 수집 완료: stockCode={}, todayOnly={}", stockCode, todayOnly);
    }

    private static void accumulateDaily(Map<String, TradeAmount> merged, String dt, String buyAmt, String sellAmt) {
        merged.merge(
                dt,
                new TradeAmount(KiwoomValueParser.parseBigDecimal(buyAmt), KiwoomValueParser.parseBigDecimal(sellAmt)),
                (a, b) -> a.add(b.buy(), b.sell()));
    }

    private record TradeAmount(BigDecimal buy, BigDecimal sell) {
        static TradeAmount zero() {
            return new TradeAmount(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        TradeAmount add(BigDecimal b, BigDecimal s) {
            return new TradeAmount(buy.add(b), sell.add(s));
        }

        BigDecimal net() {
            return buy.subtract(sell);
        }
    }
}
