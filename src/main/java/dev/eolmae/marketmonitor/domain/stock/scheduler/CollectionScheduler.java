package dev.eolmae.marketmonitor.domain.stock.scheduler;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketmonitor.domain.notification.service.RenderService;
import dev.eolmae.marketmonitor.domain.stock.collector.HoldingsSyncService;
import dev.eolmae.marketmonitor.domain.stock.collector.IndexContributionRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.IntradayInvestorRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramNetBuyRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramTradeDailyCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramTradeIntradayCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.SectorInvestorNetBuyCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ShortSellingTrendCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.StockInfoCollector;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionScheduler {

    private final HoldingsSyncService holdingsSyncService;
    private final SectorInvestorNetBuyCollector sectorInvestorNetBuyCollector;
    private final IntradayInvestorRankingCollector intradayInvestorRankingCollector;
    private final ProgramNetBuyRankingCollector programNetBuyRankingCollector;
    private final ProgramTradeIntradayCollector programTradeIntradayCollector;
    private final ProgramTradeDailyCollector programTradeDailyCollector;
    private final IndexContributionRankingCollector indexContributionRankingCollector;
    private final ShortSellingTrendCollector shortSellingTrendCollector;
    private final StockInfoCollector stockInfoCollector;
    private final RenderService renderService;
    private final EscalationPublisher escalationPublisher;

    private static final String KST_ZONE_ID = "Asia/Seoul";

    /**
     * 장중 시장 데이터 수집: 평일 08:00~20:00, 1시간 간격
     */
    @Scheduled(cron = "0 0 8-20 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectMarketData() {
        LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
        log.info("장중 시장 데이터 수집 시작: snapshotTime={}", snapshotTime);

        runSafely("보유종목동기화", holdingsSyncService::sync);
        runSafely("투자자별매매종합", () -> sectorInvestorNetBuyCollector.collect(snapshotTime));
        runSafely("장중투자자랭킹", () -> intradayInvestorRankingCollector.collect(snapshotTime));
        runSafely("프로그램매매랭킹", () -> programNetBuyRankingCollector.collect(snapshotTime));
        runSafely("프로그램매매히스토리", () -> programTradeIntradayCollector.collect(snapshotTime));
        runSafely("지수기여도랭킹", () -> indexContributionRankingCollector.collect(snapshotTime));
        // runSafely("시장현황텔레그램발송", () -> renderService.send(RenderTarget.MARKET_SUMMARY));

        log.info("장중 시장 데이터 수집 완료: snapshotTime={}", snapshotTime);
    }

    /**
     * 프로그램매매 일별 이력 수집: 평일 21:00 (장 마감 후 1회)
     */
    @Scheduled(cron = "0 0 21 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectProgramTradingDaily() {
        log.info("프로그램매매 일별 이력 수집 시작");
        runSafely("프로그램매매일별", programTradeDailyCollector::collect);
        log.info("프로그램매매 일별 이력 수집 완료");
    }

    /**
     * 공매도 데이터 수집: 평일 20:30 (당일 자료 18:30 이후 제공)
     */
    @Scheduled(cron = "0 30 20 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectShortSelling() {
        LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
        log.info("공매도 데이터 수집 시작: snapshotTime={}", snapshotTime);

        runSafely("공매도", () -> shortSellingTrendCollector.collect(snapshotTime));

        log.info("공매도 데이터 수집 완료: snapshotTime={}", snapshotTime);
    }

    /**
     * 종목 마스터 동기화: 평일 07:00 (장 시작 전)
     */
    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = KST_ZONE_ID)
    public void syncStockInfo() {
        log.info("종목 마스터 동기화 시작");

        runSafely("종목마스터", stockInfoCollector::sync);

        log.info("종목 마스터 동기화 완료");
    }

    private void runSafely(String collectorName, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            escalationPublisher.report(EscalateException.wrap(ErrorCode.COLLECTOR_EXECUTION_FAILED, e, collectorName));
        }
    }
}
