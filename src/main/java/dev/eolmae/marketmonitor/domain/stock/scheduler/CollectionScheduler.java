package dev.eolmae.marketmonitor.domain.stock.scheduler;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.util.KstClock;
import dev.eolmae.marketmonitor.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketmonitor.domain.notification.service.MarketMapTelegramReportSender;
import dev.eolmae.marketmonitor.domain.stock.collector.HoldingsSyncService;
import dev.eolmae.marketmonitor.domain.stock.collector.IndexContributionRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.IntradayInvestorRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramNetBuyRankingCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramTradeDailyCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ProgramTradeIntradayCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.SectorInvestorNetBuyCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.ShortSellingTrendCollector;
import dev.eolmae.marketmonitor.domain.stock.collector.StockInfoCollector;
import java.time.LocalDate;
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
    private final MarketMapTelegramReportSender marketMapTelegramReportSender;
    private final EscalationPublisher escalationPublisher;

    private static final String KST_ZONE_ID = "Asia/Seoul";

    /**
     * 장중 시장 데이터 수집: 평일 collect.start-hour~end-hour, interval-minutes 간격.
     * 수집 직후 마켓맵 텔레그램 발송을 매번 호출하되, 실제 발송 여부(telegram.send-minute분인지)는
     * TelegramReportSender가 자체적으로 걸러낸다(별도 스케줄로 분리하면 두 트리거의 실행 순서를
     * 보장할 수 없어, 같은 호출 안에서 순차 실행되도록 묶었다).
     */
    @Scheduled(
            cron = "0 0/${collect.interval-minutes} ${collect.start-hour}-${collect.end-hour} * * MON-FRI",
            zone = KST_ZONE_ID)
    public void collectMarketData() {
        LocalDateTime snapshotTime = KstClock.getNowTruncateMinute();

        if (isHoliday(snapshotTime.toLocalDate())) {
            return;
        }

        log.info("장중 시장 데이터 수집 시작: snapshotTime={}", snapshotTime);

        run("투자자별매매종합", () -> sectorInvestorNetBuyCollector.collect(snapshotTime));
        run("프로그램매매랭킹", () -> programNetBuyRankingCollector.collect(snapshotTime));
        run("지수기여도랭킹", () -> indexContributionRankingCollector.collect(snapshotTime));

        run("마켓맵텔레그램발송", () -> marketMapTelegramReportSender.send(snapshotTime));

        log.info("장중 시장 데이터 수집 완료: snapshotTime={}", snapshotTime);
    }

    /**
     * [구버전] 장중 시장 데이터 수집: 평일 08:00~20:00, 1시간 간격.
     * 관심종목 구조 정리가 아직 안 끝나서 collectMarketData()로 완전히 대체하지 않고 당분간
     * 비활성화 상태로 남겨둔다 — 나중에 참고하거나 필요하면 되살리기 쉽도록 코드는 그대로 유지.
     */
    // @Scheduled(cron = "0 0 8-20 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectMarketDataHourly() {
        LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
        log.info("장중 시장 데이터 수집 시작: snapshotTime={}", snapshotTime);

        run("보유종목동기화", holdingsSyncService::sync);
        run("투자자별매매종합", () -> sectorInvestorNetBuyCollector.collect(snapshotTime));
        run("장중투자자랭킹", () -> intradayInvestorRankingCollector.collect(snapshotTime));
        run("프로그램매매랭킹", () -> programNetBuyRankingCollector.collect(snapshotTime));
        run("프로그램매매히스토리", () -> programTradeIntradayCollector.collect(snapshotTime));
        run("지수기여도랭킹", () -> indexContributionRankingCollector.collect(snapshotTime));
        run("마켓맵텔레그램발송", () -> marketMapTelegramReportSender.send(snapshotTime));

        log.info("장중 시장 데이터 수집 완료: snapshotTime={}", snapshotTime);
    }

    /**
     * 프로그램매매 일별 이력 수집: 평일 21:00 (장 마감 후 1회)
     * 관심종목 구조 정리 전까지 비활성화.
     */
    // @Scheduled(cron = "0 0 21 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectProgramTradingDaily() {
        log.info("프로그램매매 일별 이력 수집 시작");
        run("프로그램매매일별", programTradeDailyCollector::collect);
        log.info("프로그램매매 일별 이력 수집 완료");
    }

    /**
     * 공매도 데이터 수집: 평일 20:30 (당일 자료 18:30 이후 제공)
     * 관심종목 구조 정리 전까지 비활성화.
     */
    // @Scheduled(cron = "0 30 20 * * MON-FRI", zone = KST_ZONE_ID)
    public void collectShortSelling() {
        LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
        log.info("공매도 데이터 수집 시작: snapshotTime={}", snapshotTime);

        run("공매도", () -> shortSellingTrendCollector.collect(snapshotTime));

        log.info("공매도 데이터 수집 완료: snapshotTime={}", snapshotTime);
    }

    /**
     * 종목 정보 동기화: 평일 07:00 (장 시작 전)
     */
    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = KST_ZONE_ID)
    public void syncStockInfo() {
        log.info("종목 정보 동기화 시작");

        run("종목정보", stockInfoCollector::sync);

        log.info("종목 정보 동기화 완료");
    }

    // TODO(#38): 실제 공휴일 판정 로직 추가 예정 — 지금은 항상 false
    private boolean isHoliday(LocalDate date) {
        return false;
    }

    private void run(String collectorName, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            escalationPublisher.report(EscalateException.wrap(ErrorCode.COLLECTOR_EXECUTION_FAILED, e, collectorName));
        }
    }
}
