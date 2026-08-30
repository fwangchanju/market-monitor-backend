package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CollectionScheduler가 한 사이클에 보내는 순서(맵 KOSPI→KOSDAQ, 섹터 KOSPI→KOSDAQ) 그대로 4건을 한
 * 번에 보내보는 수동 검증용 테스트 — 실제로 텔레그램 채팅방에 어떤 순서/모양으로 오는지 눈으로 확인하는
 * 용도. 각 마켓의 실제 마지막 수집 시각을 그대로 쓴다(현재 시각을 쓰면 장 마감/주말처럼 그 시각에 실제
 * 스냅샷이 없는 경우 섹터 발송 쪽 랭킹 조회가 빈 결과로 나옴). 배포된 컨테이너 안에서 그 환경의 실제
 * DB/renderer/텔레그램 설정을 그대로 쓰므로, 배포 후 확인할 때만 --tests로 직접 지정해서 실행한다.
 */
@SpringBootTest
class TelegramReportCycleManualTest {

    @Autowired
    private SectorPriceSnapshotService sectorPriceSnapshotService;

    @Autowired
    private MarketMapTelegramReportSender marketMapTelegramReportSender;

    @Autowired
    private MarketMapCategoryRankingTelegramReportSender marketMapCategoryRankingTelegramReportSender;

    @Test
    void sendsOneFullCycleInScheduleOrder() {
        LocalDateTime kospiTime = latestSnapshotTimeOrThrow(Market.KOSPI);
        LocalDateTime kosdaqTime = latestSnapshotTimeOrThrow(Market.KOSDAQ);

        marketMapTelegramReportSender.send(kospiTime, Market.KOSPI);
        marketMapTelegramReportSender.send(kosdaqTime, Market.KOSDAQ);
        marketMapCategoryRankingTelegramReportSender.send(kospiTime, Market.KOSPI);
        marketMapCategoryRankingTelegramReportSender.send(kosdaqTime, Market.KOSDAQ);
    }

    private LocalDateTime latestSnapshotTimeOrThrow(Market market) {
        return sectorPriceSnapshotService
                .findLatestSnapshotTime(market)
                .orElseThrow(() -> new IllegalStateException(market + " 스냅샷 데이터가 없습니다"));
    }
}
