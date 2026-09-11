package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 2시간 격자 발송용 — 마켓(KOSPI/KOSDAQ)별로 마켓맵+섹터 이미지를 한 메시지로 묶어 보낸다.
 * changeSuccess가 false면 섹터 이미지 없이 맵 이미지만 보내고 캡션에 실패 안내를 덧붙인다.
 */
@Component
@RequiredArgsConstructor
public class DailyMarketReportSender {

    private final MarketMapAndSectorTelegramReportSender marketMapAndSectorTelegramReportSender;

    public void send(LocalDateTime dataTime, boolean changeSuccess) {
        // KOSPI를 더 중요하게 본다. KOSPI가 성공하고 KOSDAQ이 실패하면 KOSPI는 그대로 나가고,
        // KOSPI가 실패하면 예외가 올라가 KOSDAQ은 시도하지 않는다. 이 두 줄의 순서가 그 규칙이다.
        marketMapAndSectorTelegramReportSender.send(dataTime, Market.KOSPI, changeSuccess);
        marketMapAndSectorTelegramReportSender.send(dataTime, Market.KOSDAQ, changeSuccess);
    }
}
