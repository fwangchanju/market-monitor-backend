package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.ShortSellingDailyHistoryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchStockBackfillService {

    private final ShortSellingTrendCollector shortSellingTrendCollector;
    private final ProgramTradeTrendCollector programTradeTrendCollector;
    private final ShortSellingDailyHistoryRepository shortSellingRepository;
    private final ProgramTradingDailyHistoryRepository programTradingDailyRepository;
    private final ProgramTradingHistoryRepository programTradingHistoryRepository;

    @Async
    public void backfill(String stockCode) {
        LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
        LocalDate yesterday = snapshotTime.toLocalDate().minusDays(1);
        log.info("관심종목 백필 시작: stockCode={}, snapshotTime={}", stockCode, snapshotTime);

        backfillShortSelling(stockCode, snapshotTime, yesterday);
        backfillProgramTradingDaily(stockCode, snapshotTime, yesterday);
        backfillProgramTradingIntraday(stockCode, snapshotTime);

        log.info("관심종목 백필 완료: stockCode={}", stockCode);
    }

    // 7번 공매도: 전일 데이터 없으면 60일 백필
    private void backfillShortSelling(String stockCode, LocalDateTime snapshotTime, LocalDate yesterday) {
        if (shortSellingRepository.existsByStockCodeAndTradeDate(stockCode, yesterday)) {
            log.debug("공매도 백필 스킵 (전일 데이터 있음): stockCode={}", stockCode);
            return;
        }
        try {
            shortSellingTrendCollector.backfill(stockCode, snapshotTime);
        } catch (Exception e) {
            log.error("공매도 백필 실패: stockCode={}", stockCode, e);
        }
    }

    // 6번 일별: 전일 데이터 없으면 60일 백필
    private void backfillProgramTradingDaily(String stockCode, LocalDateTime snapshotTime, LocalDate yesterday) {
        if (programTradingDailyRepository.existsByStockCodeAndTradeDate(stockCode, yesterday)) {
            log.debug("프로그램매매 일별 백필 스킵 (전일 데이터 있음): stockCode={}", stockCode);
            return;
        }
        try {
            programTradeTrendCollector.backfillDaily(stockCode, snapshotTime);
        } catch (Exception e) {
            log.error("프로그램매매 일별 백필 실패: stockCode={}", stockCode, e);
        }
    }

    // 6번 장중: 현재 정각 스냅샷 없으면 당일 백필
    private void backfillProgramTradingIntraday(String stockCode, LocalDateTime snapshotTime) {
        if (programTradingHistoryRepository.existsByStockCodeAndSnapshotTime(stockCode, snapshotTime)) {
            log.debug("프로그램매매 장중 백필 스킵 (현재 정각 데이터 있음): stockCode={}", stockCode);
            return;
        }
        try {
            programTradeTrendCollector.backfillIntraday(stockCode, snapshotTime);
        } catch (Exception e) {
            log.error("프로그램매매 장중 백필 실패: stockCode={}", stockCode, e);
        }
    }
}
