package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 마켓맵 이미지 렌더링 + 텔레그램 발송이 실제로 동작하는지 확인하는 수동 검증용 테스트. 캡션에 카테고리
 * 랭킹 텍스트도 같이 붙어서(CategoryRankingTextBuilder) 실제 DB 접근이 필요해 SpringBootTest로 붙인다.
 * 배포된 컨테이너 안에서 그 환경의 실제 DB/renderer/텔레그램 설정을 그대로 쓰므로, 배포 후 정상 동작하는지
 * 확인할 때만 --tests로 직접 지정해서 실행한다.
 */
@SpringBootTest
class MarketMapTelegramReportSenderManualTest {

    @Autowired
    private MarketMapTelegramReportSender sender;

    @Test
    void sendsMarketMapImageToTelegram() {
        sender.send(LocalDateTime.now(), MarketQuery.KOSPI);
    }
}
