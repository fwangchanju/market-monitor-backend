package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.domain.access.properties.AdminProperties;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.properties.MarketMonitorProperties;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSummaryRenderService {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final MarketMonitorProperties marketMonitorProperties;
    private final AdminProperties adminProperties;

    public void sendMarketSummary() {
        List<byte[]> images = screenshotClient.captureAll();
        telegramClient.sendMessage(telegramProperties.chatId(), buildAccessUrl());
        telegramClient.sendMediaGroup(telegramProperties.chatId(), images);
        log.info("시장 현황 이미지 발송 완료: {}장", images.size());
    }

    private String buildAccessUrl() {
        String token = adminProperties.tokens().getFirst();
        return marketMonitorProperties.baseUrl() + "/internal/register-ip?token=" + token
                + "&redirectTo=/market-summary";
    }
}
