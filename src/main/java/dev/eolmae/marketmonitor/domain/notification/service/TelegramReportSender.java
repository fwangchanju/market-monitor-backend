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
// target()/buildText()만 구현하면 된다. 발송 여부(발송 주기 게이팅, 데이터 존재 여부 등) 판단은 호출부
// (CollectionScheduler) 책임 — 여기선 호출되면 무조건 캡처+발송한다.
@Slf4j
@RequiredArgsConstructor
public abstract class TelegramReportSender {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;

    protected abstract RenderTarget target();

    protected abstract String buildText(LocalDateTime dataTime);

    public void send(LocalDateTime dataTime) {
        RenderTarget target = target();
        List<byte[]> images = screenshotClient.capture(target.path(), target.selector());
        byte[] combinedImage = ImageStitcher.stitchVertically(images);
        telegramClient.sendPhoto(telegramProperties.chatId(), combinedImage, buildText(dataTime));
        log.info("{} 이미지 발송 완료: {}장 결합", target, images.size());
    }
}
