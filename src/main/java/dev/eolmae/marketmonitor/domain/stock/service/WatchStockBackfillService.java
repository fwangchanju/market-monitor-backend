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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchStockBackfillService {

    private static final int INTRADAY_BACKFILL_TRADING_DAYS = 3;

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

    /** from이 주말이면 직전 거래일인(휴무는 고려하지 않고) 금요일로 반환. */
    private LocalDate previousTradingDay(LocalDate from) {
        return switch (from.getDayOfWeek()) {
            case MONDAY -> from.minusDays(3);
            case SUNDAY -> from.minusDays(2);
            default -> from.minusDays(1);
        };
    }

    // 공매도: 직전 거래일 데이터 없으면 60일 백필
    private void backfillShortSelling(WatchStock watchStock) {
        String stockCode = watchStock.getStockCode();
        LocalDate previousTradingDay = previousTradingDay(LocalDate.now(Zone.KST.zoneId()));
        if (shortSellingRepository.existsByStockCodeAndTradeDate(stockCode, previousTradingDay)) {
            log.debug(
                    "공매도 백필 스킵 (직전 거래일 데이터 있음): stockCode={}, previousTradingDay={}",
                    stockCode,
                    previousTradingDay);
            return;
        }
        try {
            shortSellingTrendCollector.backfill(watchStock);
        } catch (Exception e) {
            log.error("공매도 백필 실패: stockCode={}", stockCode, e);
        }
    }

    // 일별: 직전 거래일 데이터 없으면 60일 백필
    private void backfillProgramTradingDaily(WatchStock watchStock) {
        String stockCode = watchStock.getStockCode();
        LocalDate previousTradingDay = previousTradingDay(LocalDate.now(Zone.KST.zoneId()));
        if (programTradingDailyRepository.existsByStockCodeAndTradeDate(stockCode, previousTradingDay)) {
            log.debug(
                    "프로그램매매 일별 백필 스킵 (직전 거래일 데이터 있음): stockCode={}, previousTradingDay={}",
                    stockCode,
                    previousTradingDay);
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
        LocalDateTime latestSnapshotHour = CollectionChecker.latestSnapshotHour(date);
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
}
