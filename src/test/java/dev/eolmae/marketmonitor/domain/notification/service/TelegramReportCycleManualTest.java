package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CollectionScheduler가 실제로 부르는 것과 똑같이 DailyMarketReportSender.send()를 그대로 호출하는
 * 수동 검증용 테스트 — 스케줄러의 "몇 분에만 보낸다" 시각 게이팅은 여기선 제외하고(테스트를 아무 때나
 * 돌려도 이미지를 바로 받아봐야 하므로) 발송 로직 자체만 그대로 재현한다. 지금은 DailyMarketReportSender
 * 안에서 섹터 호출이 주석 처리돼 있어 맵 메시지 1건만 온다 — 섹터를 다시 켜면 이 테스트도 별도 수정 없이
 * 그대로 맵+섹터 2건을 보내게 된다. 각 마켓의 실제 마지막 수집 시각을 그대로 쓴다(현재 시각을 쓰면 장
 * 마감/주말처럼 그 시각에 실제 스냅샷이 없는 경우 섹터 발송 쪽 랭킹 조회가 빈 결과로 나옴). 배포된
 * 컨테이너 안에서 그 환경의 실제 DB/renderer/텔레그램 설정을 그대로 쓰므로, 배포 후 확인할 때만
 * --tests로 직접 지정해서 실행한다.
 */
@SpringBootTest
class TelegramReportCycleManualTest {

    @Autowired
    private SectorPriceSnapshotService sectorPriceSnapshotService;

    @Autowired
    private DailyMarketReportSender dailyMarketReportSender;

    @Test
    void sendsAsSchedulerDoes() {
        // findLatestCommonSnapshotTime이 markets 전부가 공통으로 가진 최신 시각을 한 번에 구해주므로,
        // 마켓별로 따로 조회해서 min을 취할 필요 없이 이 호출 하나로 충분하다.
        LocalDateTime dataTime = sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(List.of(Market.KOSPI, Market.KOSDAQ))
                .orElseThrow(() -> new IllegalStateException("스냅샷 데이터가 없습니다"));

        dailyMarketReportSender.send(dataTime, true);
    }
}
