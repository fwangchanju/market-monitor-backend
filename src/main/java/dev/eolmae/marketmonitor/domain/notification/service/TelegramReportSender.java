package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 텔레그램으로 캡처 이미지+텍스트를 발송하는 공통 흐름. 새 발송 양식이 필요하면 이 클래스를 상속해서
// target()/buildText()만 구현하면 된다.
@Slf4j
@RequiredArgsConstructor
public abstract class TelegramReportSender {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;

    protected abstract RenderTarget target();

    protected abstract String buildText(LocalDateTime dataTime);

    protected boolean isNotSendTime(LocalDateTime snapshotTime) {
        return snapshotTime.getMinute() != telegramProperties.sendMinute();
    }

    public void send(LocalDateTime snapshotTime) {
        send(snapshotTime, snapshotTime);
    }

    // snapshotTime: 발송 여부 판단 기준(실제 시각). dataTime: 캡션에 찍을 데이터 기준 시각 — 마감 이후엔
    // 수집이 스킵되어 실제 시각과 달라질 수 있어(예: 20:10에 발송되지만 데이터는 20:00 기준), 둘을 분리했다.
    public void send(LocalDateTime snapshotTime, LocalDateTime dataTime) {
        if (isNotSendTime(snapshotTime)) {
            return;
        }

        RenderTarget target = target();
        List<byte[]> images = screenshotClient.capture(target.path(), target.selector());
        byte[] combinedImage = ImageStitcher.stitchVertically(images);
        telegramClient.sendPhoto(telegramProperties.chatId(), combinedImage, buildText(dataTime));
        log.info("{} 이미지 발송 완료: {}장 결합", target, images.size());
    }
}
