package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.domain.stock.ProgramTradingDailyHistory;
import dev.eolmae.marketmonitor.domain.stock.ProgramTradingHistory;
import dev.eolmae.marketmonitor.domain.stock.WatchStockCacheService;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.DailyProgramTradeTrendRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.DailyProgramTradeTrendResponse;
import dev.eolmae.marketmonitor.domain.stock.dto.HourlyProgramTradeTrendRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.HourlyProgramTradeTrendResponse;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ka90008: 종목시간별프로그램매매추이 / ka90013: 종목일별프로그램매매추이
// ka90008: 각 틱은 해당 마켓(KRX/NXT)의 당일 누적합 → KRX 최신 틱 + NXT 최신 틱 합산값만 저장
// ka90013: 각 틱은 해당 날짜의 일별 합계 → KRX[date] + NXT[date] 합산 저장
// 순매수금액은 prm_netprps_amt 미사용 (-- 파싱 오류) → buy - sell 직접 계산
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramTradeTrendCollector {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");

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

    private final KiwoomApiClient kiwoomApiClient;
    private final ProgramTradingHistoryRepository historyRepository;
    private final ProgramTradingDailyHistoryRepository dailyHistoryRepository;
    private final WatchStockCacheService watchStockCacheService;

    /** 스케줄러 호출 — 당일 장중 스냅샷 적재 */
    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        List<String> stockCodes = watchStockCacheService.findDistinctStockCodes();
        for (String stockCode : stockCodes) {
            try {
                collectIntradayForStock(stockCode, snapshotTime);
            } catch (Exception e) {
                log.error("프로그램매매 장중이력 수집 실패: stockCode={}", stockCode, e);
            }
        }
    }

    /** 스케줄러 호출 — 당일 일별 데이터만 적재 */
    @Transactional
    public void collectDaily(LocalDate tradeDate) {
        List<String> stockCodes = watchStockCacheService.findDistinctStockCodes();
        for (String stockCode : stockCodes) {
            try {
                collectDailyForStock(stockCode, tradeDate, true);
            } catch (Exception e) {
                log.error("프로그램매매 일별이력 수집 실패: stockCode={}", stockCode, e);
            }
        }
    }

    /** 관심종목 신규 등록 시 백필 — 당일 hourly 스냅샷 역산 + 과거 일별 적재, 비동기 호출 */
    @Transactional
    public void backfill(String stockCode, LocalDateTime snapshotTime) {
        backfillIntraday(stockCode, snapshotTime);
        backfillDaily(stockCode, snapshotTime);
        log.info("프로그램매매 백필 완료: stockCode={}", stockCode);
    }

    @Transactional
    public void backfillDaily(String stockCode, LocalDateTime snapshotTime) {
        collectDailyForStock(stockCode, snapshotTime.toLocalDate(), false);
    }

    private void collectIntradayForStock(String stockCode, LocalDateTime snapshotTime) {
        if (historyRepository.existsByStockCodeAndSnapshotTime(stockCode, snapshotTime)) {
            log.debug("프로그램매매 장중이력 이미 존재, 스킵: stockCode={}, snapshotTime={}", stockCode, snapshotTime);
            return;
        }

        String dateStr = snapshotTime.format(DATE_FMT);

        var krxRequest = new HourlyProgramTradeTrendRequest(stockCode, StexType.KRX.code(), dateStr);
        HourlyProgramTradeTrendResponse krxResponse =
                kiwoomApiClient.post(krxRequest, HourlyProgramTradeTrendResponse.class);

        var nxtRequest = new HourlyProgramTradeTrendRequest(stockCode + "_NX", StexType.KRX.code(), dateStr);
        HourlyProgramTradeTrendResponse nxtResponse =
                kiwoomApiClient.post(nxtRequest, HourlyProgramTradeTrendResponse.class);

        List<HourlyProgramTradeTrendResponse.TradeTick> krxTicks =
                krxResponse.ticks() != null ? krxResponse.ticks() : List.of();
        List<HourlyProgramTradeTrendResponse.TradeTick> nxtTicks =
                nxtResponse.ticks() != null ? nxtResponse.ticks() : List.of();

        if (krxTicks.isEmpty() && nxtTicks.isEmpty()) {
            log.debug("프로그램매매 장중이력 없음: stockCode={}", stockCode);
            return;
        }

        TradeAmount amounts = sumLatestTicks(krxTicks, nxtTicks);
        historyRepository.save(
                ProgramTradingHistory.create(stockCode, snapshotTime, amounts.buy(), amounts.sell(), amounts.net()));

        log.debug("프로그램매매 장중이력 수집 완료: stockCode={}", stockCode);
    }

    /**
     * 백필용 — ka90008 tm 필드로 당일 과거 정각 스냅샷 역산 적재. WatchStockBackfillService에서 가드 통과 후 호출.
     * 09:00 스냅샷 = tm < 090000 인 KRX+NXT 최신 틱 합산.
     * 범위: 08:00 ~ snapshotTime(현재 정각).
     */
    @Transactional
    public void backfillIntraday(String stockCode, LocalDateTime snapshotTime) {
        String dateStr = snapshotTime.format(DATE_FMT);

        var krxResponse = kiwoomApiClient.post(
                new HourlyProgramTradeTrendRequest(stockCode, StexType.KRX.code(), dateStr),
                HourlyProgramTradeTrendResponse.class);
        var nxtResponse = kiwoomApiClient.post(
                new HourlyProgramTradeTrendRequest(stockCode + "_NX", StexType.KRX.code(), dateStr),
                HourlyProgramTradeTrendResponse.class);

        List<HourlyProgramTradeTrendResponse.TradeTick> krxTicks =
                krxResponse.ticks() != null ? krxResponse.ticks() : List.of();
        List<HourlyProgramTradeTrendResponse.TradeTick> nxtTicks =
                nxtResponse.ticks() != null ? nxtResponse.ticks() : List.of();

        LocalDate today = snapshotTime.toLocalDate();
        LocalDateTime currentHour = snapshotTime.withMinute(0).withSecond(0).withNano(0);

        Set<String> existingSnapshots = historyRepository.findSnapshotTimesByStockCodeAndDate(stockCode, today).stream()
                .map(t -> t.format(TIME_FMT))
                .collect(Collectors.toSet());

        LocalDateTime scheduleStart = LocalDateTime.of(today, LocalTime.of(8, 0));
        while (!scheduleStart.isAfter(currentHour)) {
            LocalTime boundary = scheduleStart.toLocalTime();
            if (!existingSnapshots.contains(scheduleStart.format(TIME_FMT))) {

                HourlyProgramTradeTrendResponse.TradeTick krxLatest = krxTicks.stream()
                        .filter(t -> t.tm() != null
                                && LocalTime.parse(t.tm(), TIME_FMT).isBefore(boundary))
                        .max(Comparator.comparing(t -> LocalTime.parse(t.tm(), TIME_FMT)))
                        .orElse(null);

                HourlyProgramTradeTrendResponse.TradeTick nxtLatest = nxtTicks.stream()
                        .filter(t -> t.tm() != null
                                && LocalTime.parse(t.tm(), TIME_FMT).isBefore(boundary))
                        .max(Comparator.comparing(t -> LocalTime.parse(t.tm(), TIME_FMT)))
                        .orElse(null);

                if (krxLatest != null || nxtLatest != null) {
                    TradeAmount amounts = sumLatestTicks(
                            krxLatest != null ? List.of(krxLatest) : List.of(),
                            nxtLatest != null ? List.of(nxtLatest) : List.of());
                    historyRepository.save(ProgramTradingHistory.create(
                            stockCode, scheduleStart, amounts.buy(), amounts.sell(), amounts.net()));
                }
            }
            scheduleStart = scheduleStart.plusHours(1);
        }
    }

    private void collectDailyForStock(String stockCode, LocalDate targetDate, boolean todayOnly) {
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
            String dt = tick.dt() != null ? tick.dt().trim() : null;
            if (dt == null || dt.isBlank()) {
                continue;
            }
            accumulateDaily(merged, dt, tick.prmBuyAmt(), tick.prmSellAmt());
        }
        for (DailyProgramTradeTrendResponse.DailyTick tick : nxtTicks) {
            String dt = tick.dt() != null ? tick.dt().trim() : null;
            if (dt == null || dt.isBlank()) {
                continue;
            }
            accumulateDaily(merged, dt, tick.prmBuyAmt(), tick.prmSellAmt());
        }

        for (Map.Entry<String, TradeAmount> entry : merged.entrySet()) {
            LocalDate date = parseDate(entry.getKey());
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

    private static TradeAmount sumLatestTicks(
            List<HourlyProgramTradeTrendResponse.TradeTick> krxTicks,
            List<HourlyProgramTradeTrendResponse.TradeTick> nxtTicks) {

        HourlyProgramTradeTrendResponse.TradeTick krxLatest = krxTicks.isEmpty() ? null : krxTicks.getLast();
        HourlyProgramTradeTrendResponse.TradeTick nxtLatest = nxtTicks.isEmpty() ? null : nxtTicks.getLast();

        TradeAmount result = TradeAmount.zero();
        if (krxLatest != null) {
            result = result.add(
                    NumberParser.parseBigDecimal(krxLatest.prmBuyAmt()),
                    NumberParser.parseBigDecimal(krxLatest.prmSellAmt()));
        }
        if (nxtLatest != null) {
            result = result.add(
                    NumberParser.parseBigDecimal(nxtLatest.prmBuyAmt()),
                    NumberParser.parseBigDecimal(nxtLatest.prmSellAmt()));
        }
        return result;
    }

    private static void accumulateDaily(Map<String, TradeAmount> merged, String dt, String buyAmt, String sellAmt) {
        merged.merge(
                dt,
                new TradeAmount(NumberParser.parseBigDecimal(buyAmt), NumberParser.parseBigDecimal(sellAmt)),
                (a, b) -> a.add(b.buy(), b.sell()));
    }

    private static LocalDate parseDate(String dt) {
        try {
            return LocalDate.of(
                    Integer.parseInt(dt.substring(0, 4)),
                    Integer.parseInt(dt.substring(4, 6)),
                    Integer.parseInt(dt.substring(6, 8)));
        } catch (Exception e) {
            return null;
        }
    }
}
