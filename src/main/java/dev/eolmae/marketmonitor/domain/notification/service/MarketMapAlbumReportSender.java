package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.dto.MergedTopCategoryRanking;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 하루 세 번(telegram.map-send-times) 코스피+코스닥 맵을 앨범 하나로 묶어 보낸다. 기존
 * {@link MarketMapTelegramReportSender}(마켓 하나, {@link TelegramReportSender} 상속,
 * collectMarketDataHourly 전용이라 지금은 비활성)와는 별개의 발송기다. 그쪽은 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketMapAlbumReportSender {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final CategoryRankingTextBuilder categoryRankingTextBuilder;
    private final MarketMapQueryService marketMapQueryService;

    /**
     * sectorAvailable이 false여도 맵은 보낸다 — 맵 이미지는 카테고리 등락률 스냅샷과 무관하다. 다만
     * 캡션(카테고리 등락률 기반)은 못 만드므로 캡션 없이 이미지만 보낸다.
     */
    public void send(LocalDateTime dataTime, boolean sectorAvailable) {
        MergedTopCategoryRanking merged =
                marketMapQueryService.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, dataTime);

        List<byte[]> images = capture(merged.markets());
        // 결과에 있는 마켓만 캡처한다 — SectorTelegramReportSender와 같은 규칙이다. 한 장도 못
        // 보냈으면(두 마켓 다 그 시각 데이터가 없거나 캡처 자체가 비면) 캡처가 통째로 빈 것이다.
        if (images.isEmpty()) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        }

        String caption = sectorAvailable ? categoryRankingTextBuilder.buildMapCaption(merged.topCategories()) : null;
        sendImages(images, caption);
        log.info("맵 리포트 발송 완료: 마켓={}건, 이미지={}장", merged.markets().size(), images.size());
    }

    private List<byte[]> capture(List<Market> markets) {
        return markets.stream()
                .flatMap(market ->
                        screenshotClient.capture(mapPath(market), RenderTarget.MARKET_MAP.selector()).stream())
                .toList();
    }

    // 텔레그램 sendMediaGroup은 media가 2~10개여야 한다 — 한 마켓만 캡처됐으면 sendPhoto로 내려간다.
    private void sendImages(List<byte[]> images, String caption) {
        if (images.size() == 1) {
            telegramClient.sendPhoto(telegramProperties.chatId(), images.get(0), caption);
            return;
        }
        telegramClient.sendMediaGroup(telegramProperties.chatId(), images, caption);
    }

    private String mapPath(Market market) {
        return RenderTarget.MARKET_MAP.path() + "?market=" + market.name();
    }
}
