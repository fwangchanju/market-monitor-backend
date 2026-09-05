package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 5분 주기 발송용 — 마켓맵(KOSPI+KOSDAQ)과 섹터를 각각 별도 메시지로 보낸다. changeSuccess가 false면
 * 섹터 이미지 대신 실패 안내 텍스트만 보낸다.
 * 섹터(카테고리 랭킹) 발송은 당분간 비활성화 상태로 유지한다.
 */
@Component
@RequiredArgsConstructor
public class DailyMarketReportSender {

    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final MarketMapTelegramReportSender marketMapTelegramReportSender;
    private final MarketMapCategoryRankingTelegramReportSender marketMapCategoryRankingTelegramReportSender;

    public void send(LocalDateTime dataTime, boolean changeSuccess) {
        marketMapTelegramReportSender.send(dataTime, MarketQuery.ALL_STOCKS);

        //        if (changeSuccess) {
        //            marketMapCategoryRankingTelegramReportSender.send(dataTime, MarketQuery.ALL_STOCKS);
        //        } else {
        //            telegramClient.sendMessage(telegramProperties.chatId(), "섹터 이미지 생성에 실패했습니다");
        //        }
    }
}
