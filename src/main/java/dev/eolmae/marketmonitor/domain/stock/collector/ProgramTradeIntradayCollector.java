package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.HourlyProgramTradeTrendRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.HourlyProgramTradeTrendResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingHistory;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockCacheService;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ka90008: 종목시간별프로그램매매추이
// 각 틱은 해당 마켓(KRX/NXT)의 당일 누적합 → KRX 최신 틱 + NXT 최신 틱 합산값만 저장
// 순매수금액은 prm_netprps_amt 미사용 (-- 파싱 오류) → buy - sell 직접 계산
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramTradeIntradayCollector {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimePattern.TIME.formatter();

    private final KiwoomApiClient kiwoomApiClient;
    private final ProgramTradingHistoryRepository historyRepository;
    private final WatchStockCacheService watchStockCacheService;

    /** 스케줄러 호출 — 당일 장중 스냅샷 적재 */
    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        List<WatchStock> watchStocks = watchStockCacheService.getCache();
        for (WatchStock watchStock : watchStocks) {
            try {
                collectForStock(watchStock, snapshotTime);
            } catch (Exception e) {
                log.error("프로그램매매 장중이력 수집 실패: stockCode={}", watchStock.getStockCode(), e);
            }
        }
    }

    /**
     * 백필용 — ka90008 tm 필드로 당일 과거 정각 스냅샷 역산 적재. WatchStockBackfillService에서 가드 통과 후 호출.
     * 09:00 스냅샷 = tm < 090000 인 KRX+NXT 최신 틱 합산.
     * 범위: 08:00 ~ snapshotTime(현재 정각).
     */
    @Transactional
    public void backfill(WatchStock watchStock, LocalDateTime snapshotTime) {
        String stockCode = watchStock.getStockCode();
        String dateStr = snapshotTime.format(DateTimePattern.DATE.formatter());

        Queue<HourlyProgramTradeTrendResponse.TradeTick> krxQueue =
                sortedQueue(fetchProgramTradeTicks(stockCode, dateStr));
        Queue<HourlyProgramTradeTrendResponse.TradeTick> nxtQueue =
                sortedQueue(fetchProgramTradeTicks(stockCode + "_NX", dateStr));

        LocalDate snapshotDate = snapshotTime.toLocalDate();
        LocalDateTime snapshotHour = snapshotTime.truncatedTo(ChronoUnit.HOURS);

        List<LocalDateTime> existingSnapshots = historyRepository.findSnapshotTimesByStockCodeAndDate(stockCode, snapshotDate);

        HourlyProgramTradeTrendResponse.TradeTick krxTick = null;
        HourlyProgramTradeTrendResponse.TradeTick nxtTick = null;

        LocalDateTime scheduleStart = LocalDateTime.of(snapshotDate, LocalTime.of(8, 0));
        while (!scheduleStart.isAfter(snapshotHour)) {
            LocalTime boundary = scheduleStart.toLocalTime();

            while (hasEarlierTick(krxQueue, boundary)) {
                krxTick = krxQueue.poll();
            }
            while (hasEarlierTick(nxtQueue, boundary)) {
                nxtTick = nxtQueue.poll();
            }

            if (!existingSnapshots.contains(scheduleStart)) {
                TradeAmount amounts = combine(krxTick, nxtTick);
                if (amounts.hasAmount()) {
                    historyRepository.save(toEntity(stockCode, scheduleStart, amounts));
                }
            }
            scheduleStart = scheduleStart.plusHours(1);
        }
    }

    private void collectForStock(WatchStock watchStock, LocalDateTime snapshotTime) {
        String stockCode = watchStock.getStockCode();
        if (historyRepository.existsByStockCodeAndSnapshotTime(stockCode, snapshotTime)) {
            log.debug("프로그램매매 장중이력 이미 존재, 스킵: stockCode={}, snapshotTime={}", stockCode, snapshotTime);
            return;
        }

        String dateStr = snapshotTime.format(DateTimePattern.DATE.formatter());

        HourlyProgramTradeTrendResponse.TradeTick krxTick = fetchLatestTick(stockCode, dateStr);
        HourlyProgramTradeTrendResponse.TradeTick nxtTick = fetchLatestTick(stockCode + "_NX", dateStr);

        if (krxTick == null && nxtTick == null) {
            log.debug("프로그램매매 장중이력 없음: stockCode={}", stockCode);
            return;
        }

        historyRepository.save(toEntity(stockCode, snapshotTime, combine(krxTick, nxtTick)));

        log.debug("프로그램매매 장중이력 수집 완료: stockCode={}", stockCode);
    }

    private HourlyProgramTradeTrendResponse.TradeTick fetchLatestTick(String stockCode, String dateStr) {
        List<HourlyProgramTradeTrendResponse.TradeTick> ticks = fetchProgramTradeTicks(stockCode, dateStr);
        return ticks.isEmpty() ? null : ticks.getLast();
    }

    private List<HourlyProgramTradeTrendResponse.TradeTick> fetchProgramTradeTicks(String stockCode, String dateStr) {
        var request = new HourlyProgramTradeTrendRequest(stockCode, StexType.KRX.code(), dateStr);
        HourlyProgramTradeTrendResponse response = kiwoomApiClient.post(request, HourlyProgramTradeTrendResponse.class);
        return response.ticks() == null || response.ticks().isEmpty() ? List.of() : response.ticks();
    }

    private static TradeAmount combine(
            HourlyProgramTradeTrendResponse.TradeTick krxTick, HourlyProgramTradeTrendResponse.TradeTick nxtTick) {
        return TradeAmount.from(krxTick).add(TradeAmount.from(nxtTick));
    }

    private static ProgramTradingHistory toEntity(String stockCode, LocalDateTime snapshotTime, TradeAmount amounts) {
        return ProgramTradingHistory.create(stockCode, snapshotTime, amounts.buy(), amounts.sell(), amounts.net());
    }

    private static Queue<HourlyProgramTradeTrendResponse.TradeTick> sortedQueue(
            List<HourlyProgramTradeTrendResponse.TradeTick> ticks) {
        return ticks.stream()
                .filter(t -> t.tm() != null)
                .sorted(Comparator.comparing(HourlyProgramTradeTrendResponse.TradeTick::tm))
                .collect(Collectors.toCollection(ArrayDeque::new));
    }

    private static boolean hasEarlierTick(Queue<HourlyProgramTradeTrendResponse.TradeTick> queue, LocalTime boundary) {
        return !queue.isEmpty()
                && LocalTime.parse(queue.peek().tm(), TIME_FORMATTER).isBefore(boundary);
    }

    private record TradeAmount(BigDecimal buy, BigDecimal sell) {
        static TradeAmount zero() {
            return new TradeAmount(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        static TradeAmount from(HourlyProgramTradeTrendResponse.TradeTick tick) {
            if (tick == null) {
                return zero();
            }
            return new TradeAmount(
                    KiwoomValueParser.parseBigDecimal(tick.prmBuyAmt()),
                    KiwoomValueParser.parseBigDecimal(tick.prmSellAmt()));
        }

        TradeAmount add(TradeAmount other) {
            return new TradeAmount(buy.add(other.buy), sell.add(other.sell));
        }

        BigDecimal net() {
            return buy.subtract(sell);
        }

        boolean hasAmount() {
            return buy.signum() != 0 || sell.signum() != 0;
        }
    }
}
