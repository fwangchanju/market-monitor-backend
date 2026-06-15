package dev.eolmae.marketmonitor.domain.stock.scheduler;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.collector.HoldingsSyncService;
import dev.eolmae.marketmonitor.domain.stock.collector.IndexContributionRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.IntradayInvestorRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramNetBuyRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramTradeTrendCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.SectorCurrentPriceCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.SectorInvestorNetBuyCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ShortSellingTrendCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.StockInfoCollector;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "stock.collection.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CollectionScheduler {

    private final HoldingsSyncService holdingsSyncService;
    private final SectorCurrentPriceCollector sectorCurrentPriceCollector;
    private final SectorInvestorNetBuyCollector sectorInvestorNetBuyCollector;
    private final IntradayInvestorRankingCollector intradayInvestorRankingCollector;
    private final ProgramNetBuyRankingCollector programNetBuyRankingCollector;
    private final ProgramTradeTrendCollector programTradeTrendCollector;
    private final IndexContributionRankingCollector indexContributionRankingCollector;
    private final ShortSellingTrendCollector shortSellingTrendCollector;
    private final StockInfoCollector stockInfoCollector;

    private static final String KST_ZONE_ID = "Asia/Seoul";

    /**
     * 장중 시장 데이터 수집: 평일 08:00~20:00, 1시간 간격
     */
    @Scheduled(cron = "0 0 8-20 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectMarketData() {
        LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
        log.info("장중 시장 데이터 수집 시작: snapshotTime={}", snapshotTime);

        runSafely("보유종목동기화", holdingsSyncService::sync);
        runSafely("시장종합", () -> sectorCurrentPriceCollector.collect(snapshotTime));
        runSafely("투자자별매매종합", () -> sectorInvestorNetBuyCollector.collect(snapshotTime));
        runSafely("장중투자자랭킹", () -> intradayInvestorRankingCollector.collect(snapshotTime));
        runSafely("프로그램매매랭킹", () -> programNetBuyRankingCollector.collect(snapshotTime));
        runSafely("프로그램매매히스토리", () -> programTradeTrendCollector.collect(snapshotTime));
        runSafely("지수기여도랭킹", () -> indexContributionRankingCollector.collect(snapshotTime));

        log.info("장중 시장 데이터 수집 완료: snapshotTime={}", snapshotTime);
    }

    /**
     * 프로그램매매 일별 이력 수집: 평일 21:00 (장 마감 후 1회)
     */
    @Scheduled(cron = "0 0 21 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectProgramTradingDaily() {
        LocalDate tradeDate = LocalDate.now(Zone.KST.zoneId());
        log.info("프로그램매매 일별 이력 수집 시작: tradeDate={}", tradeDate);
        runSafely("프로그램매매일별", () -> programTradeTrendCollector.collectDaily(tradeDate));
        log.info("프로그램매매 일별 이력 수집 완료: tradeDate={}", tradeDate);
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
            log.error("수집기 실행 실패: collector={}", collectorName, e);
        }
    }
}
