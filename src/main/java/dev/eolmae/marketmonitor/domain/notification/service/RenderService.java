package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import java.time.LocalDateTime;
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

    public void send(RenderTarget target, LocalDateTime snapshotTime) {
        List<byte[]> images = screenshotClient.capture(target.path(), target.selector());
        byte[] combinedImage = ImageStitcher.stitchVertically(images);
        telegramClient.sendMessage(
                telegramProperties.chatId(),
                "KOSPI MAP\n" + snapshotTime.format(DateTimePattern.DATETIME_MINUTE.formatter()));
        telegramClient.sendPhoto(telegramProperties.chatId(), combinedImage);
        log.info("{} 이미지 발송 완료: {}장 결합", target, images.size());
    }
}
