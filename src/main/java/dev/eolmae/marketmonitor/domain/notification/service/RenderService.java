package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.domain.access.properties.AdminProperties;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
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
public class RenderService {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final MarketMonitorProperties marketMonitorProperties;
    private final AdminProperties adminProperties;

    public void send(RenderTarget target) {
        List<byte[]> images = screenshotClient.capture(target.path(), target.selector());
        byte[] combinedImage = ImageStitcher.stitchVertically(images);
        // telegramClient.sendMessage(telegramProperties.chatId(), buildAccessUrl(target));
        telegramClient.sendPhoto(telegramProperties.chatId(), combinedImage);
        log.info("{} 이미지 발송 완료: {}장 결합", target, images.size());
    }

    private String buildAccessUrl(RenderTarget target) {
        String token = adminProperties.tokens().getFirst();
        return marketMonitorProperties.baseUrl() + "/internal/register-ip?token=" + token + "&redirectTo="
                + target.path();
    }
}
