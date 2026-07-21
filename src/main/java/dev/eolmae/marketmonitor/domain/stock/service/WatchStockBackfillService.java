package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramTradeDailyCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramTradeIntradayCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ShortSellingTrendCollector;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ShortSellingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.util.CollectionChecker;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchStockBackfillService {

    private static final int INTRADAY_BACKFILL_TRADING_DAYS = 3;
    private static final LocalTime SHORT_SELLING_SCHEDULE_TIME = LocalTime.of(20, 30);
    private static final LocalTime PROGRAM_TRADING_DAILY_SCHEDULE_TIME = LocalTime.of(21, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(20, 0);

    private final ShortSellingTrendCollector shortSellingTrendCollector;
    private final ProgramTradeIntradayCollector programTradeIntradayCollector;
    private final ProgramTradeDailyCollector programTradeDailyCollector;
    private final ShortSellingDailyHistoryRepository shortSellingRepository;
    private final ProgramTradingDailyHistoryRepository programTradingDailyRepository;
    private final ProgramTradingHistoryRepository programTradingHistoryRepository;

    @Async
    public void backfill(WatchStock watchStock) {
        String stockCode = watchStock.getStockCode();
        log.info("관심종목 백필 시작: stockCode={}", stockCode);

        backfillShortSelling(watchStock);
        backfillProgramTradingDaily(watchStock);
        backfillProgramTradingIntraday(watchStock);

        log.info("관심종목 백필 완료: stockCode={}", stockCode);
    }

    /** scheduleTime 기준 오늘 수집이 이미 끝났으면 오늘을, 아니면 직전 거래일을 반환. */
    private LocalDate targetTradeDate(LocalTime scheduleTime) {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        boolean todayAlreadyCollected =
                CollectionChecker.isWeekday(today) && now.isAfter(LocalDateTime.of(today, scheduleTime));
        return todayAlreadyCollected ? today : CollectionChecker.previousTradingDay(today);
    }

    // 공매도 (20:30 스케줄): 대상일 데이터 없으면 60일 백필
    private void backfillShortSelling(WatchStock watchStock) {
        String stockCode = watchStock.getStockCode();
        LocalDate targetTradeDate = targetTradeDate(SHORT_SELLING_SCHEDULE_TIME);
        if (shortSellingRepository.existsByStockCodeAndTradeDate(stockCode, targetTradeDate)) {
            log.debug("공매도 백필 스킵 (대상일 데이터 있음): stockCode={}, targetTradeDate={}", stockCode, targetTradeDate);
            return;
        }
        try {
            shortSellingTrendCollector.backfill(watchStock, targetTradeDate);
        } catch (Exception e) {
            log.error("공매도 백필 실패: stockCode={}", stockCode, e);
        }
    }

    // 일별 (21:00 스케줄): 대상일 데이터 없으면 60일 백필
    private void backfillProgramTradingDaily(WatchStock watchStock) {
        String stockCode = watchStock.getStockCode();
        LocalDate targetTradeDate = targetTradeDate(PROGRAM_TRADING_DAILY_SCHEDULE_TIME);
        if (programTradingDailyRepository.existsByStockCodeAndTradeDate(stockCode, targetTradeDate)) {
            log.debug("프로그램매매 일별 백필 스킵 (대상일 데이터 있음): stockCode={}, targetTradeDate={}", stockCode, targetTradeDate);
            return;
        }
        try {
            programTradeDailyCollector.backfill(watchStock);
        } catch (Exception e) {
            log.error("프로그램매매 일별 백필 실패: stockCode={}", stockCode, e);
        }
    }

    // 장중: 최근 3거래일에 대해 날짜별로 완전성 체크 후 부족한 날짜만 백필 (ka90008은 하루 1콜이라 날짜별 게이트 필요)
    private void backfillProgramTradingIntraday(WatchStock watchStock) {
        LocalDate cursor = LocalDate.now(Zone.KST.zoneId());
        int tradingDaysCovered = 0;
        while (tradingDaysCovered < INTRADAY_BACKFILL_TRADING_DAYS) {
            if (CollectionChecker.isWeekday(cursor)) {
                backfillProgramTradingIntradayForDate(watchStock, cursor);
                tradingDaysCovered++;
            }
            cursor = cursor.minusDays(1);
        }
    }

    private void backfillProgramTradingIntradayForDate(WatchStock watchStock, LocalDate date) {
        String stockCode = watchStock.getStockCode();
        LocalDateTime latestSnapshotHour = latestSnapshotHour(date);
        if (programTradingHistoryRepository.existsByStockCodeAndSnapshotTime(stockCode, latestSnapshotHour)) {
            log.debug("프로그램매매 장중 백필 스킵 (해당일 최신 스냅샷 있음): stockCode={}, date={}", stockCode, date);
            return;
        }
        try {
            programTradeIntradayCollector.backfill(watchStock, latestSnapshotHour);
        } catch (Exception e) {
            log.error("프로그램매매 장중 백필 실패: stockCode={}, date={}", stockCode, date, e);
        }
    }

    /** date가 오늘보다 과거면 무조건 마감(20시) 시각, 오늘이면 현재 정각과 마감 중 이른 쪽을 반환. */
    private LocalDateTime latestSnapshotHour(LocalDate date) {
        LocalDateTime close = LocalDateTime.of(date, MARKET_CLOSE);
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        if (date.isBefore(now.toLocalDate())) {
            return close;
        }
        LocalDateTime currentHour = now.truncatedTo(ChronoUnit.HOURS);
        return currentHour.isBefore(close) ? currentHour : close;
    }
}
