package dev.eolmae.marketmonitor.collector;

import dev.eolmae.marketmonitor.domain.history.repository.ProgramTradingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.history.repository.ProgramTradingHistoryRepository;
import dev.eolmae.marketmonitor.domain.history.repository.ShortSellingDailyHistoryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchStockBackfillService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ShortSellingCollector shortSellingCollector;
    private final ProgramTradingCollector programTradingCollector;
    private final ShortSellingDailyHistoryRepository shortSellingRepository;
    private final ProgramTradingDailyHistoryRepository programTradingDailyRepository;
    private final ProgramTradingHistoryRepository programTradingHistoryRepository;

    @Async
    public void backfill(String stockCode) {
        LocalDateTime snapshotTime =
                LocalDateTime.now(KST).withMinute(0).withSecond(0).withNano(0);
        LocalDate yesterday = snapshotTime.toLocalDate().minusDays(1);
        log.info("관심종목 백필 시작: stockCode={}, snapshotTime={}", stockCode, snapshotTime);

        // 7번 공매도: 전일 데이터 없으면 60일 백필
        if (!shortSellingRepository.existsByStockCodeAndTradeDate(stockCode, yesterday)) {
            try {
                shortSellingCollector.backfill(stockCode, snapshotTime);
            } catch (Exception e) {
                log.error("공매도 백필 실패: stockCode={}", stockCode, e);
            }
        } else {
            log.debug("공매도 백필 스킵 (전일 데이터 있음): stockCode={}", stockCode);
        }

        // 6번 일별: 전일 데이터 없으면 60일 백필
        if (!programTradingDailyRepository.existsByStockCodeAndTradeDate(stockCode, yesterday)) {
            try {
                programTradingCollector.backfillDaily(stockCode, snapshotTime);
            } catch (Exception e) {
                log.error("프로그램매매 일별 백필 실패: stockCode={}", stockCode, e);
            }
        } else {
            log.debug("프로그램매매 일별 백필 스킵 (전일 데이터 있음): stockCode={}", stockCode);
        }

        // 6번 장중: 현재 정각 스냅샷 없으면 당일 백필
        if (!programTradingHistoryRepository.existsByStockCodeAndSnapshotTime(stockCode, snapshotTime)) {
            try {
                programTradingCollector.backfillIntraday(stockCode, snapshotTime);
            } catch (Exception e) {
                log.error("프로그램매매 장중 백필 실패: stockCode={}", stockCode, e);
            }
        } else {
            log.debug("프로그램매매 장중 백필 스킵 (현재 정각 데이터 있음): stockCode={}", stockCode);
        }

        log.info("관심종목 백필 완료: stockCode={}", stockCode);
    }
}
