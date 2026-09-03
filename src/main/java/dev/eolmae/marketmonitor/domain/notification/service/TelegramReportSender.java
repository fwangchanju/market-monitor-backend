package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
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
// (CollectionScheduler) 책임 — 여기선 호출되면 무조건 캡처+발송한다. markets는 캡처할 프론트 페이지가
// 어떤 마켓 상태로 렌더링돼야 하는지(URL 쿼리로 전달, 마켓별로 각각 한 번씩) + 캡션 텍스트 구성에 공통으로
// 쓰인다. 마켓별 캡처 결과는 서로 합치지 않고 각각 별개 사진으로 앨범(sendMediaGroup) 하나에 담아
// 알림 1번으로 보낸다.
@Slf4j
@RequiredArgsConstructor
public abstract class TelegramReportSender {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;

    protected abstract RenderTarget target();

    protected abstract String buildText(LocalDateTime dataTime, List<Market> markets);

    public void send(LocalDateTime dataTime, List<Market> markets) {
        RenderTarget target = target();
        List<byte[]> images = markets.stream()
                .flatMap(market -> screenshotClient.capture(target.path() + "?market=" + market, target.selector()).stream())
                .toList();
        telegramClient.sendMediaGroup(telegramProperties.chatId(), images, buildText(dataTime, markets));
        log.info("{}({}) 이미지 발송 완료: {}장", target, markets, images.size());
    }
}
