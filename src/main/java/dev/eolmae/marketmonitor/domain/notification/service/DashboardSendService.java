package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.ScreenshotClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "renderer.enabled", havingValue = "true")
public class DashboardSendService {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    public int sendDashboard() {
        List<byte[]> images = screenshotClient.captureAll();
        if (images.isEmpty()) {
            log.warn("캡처된 이미지 없음 — 텔레그램 발송 생략");
            return 0;
        }

        String chatId = properties.chatId();
        int sent = 0;
        for (byte[] image : images) {
            try {
                telegramClient.sendPhoto(chatId, image, null);
                sent++;
            } catch (Exception e) {
                log.error("이미지 발송 실패 ({}번째): {}", sent + 1, e.getMessage());
            }
        }

        log.info("대시보드 발송 완료: {}개 전송", sent);
        return sent;
    }
}
