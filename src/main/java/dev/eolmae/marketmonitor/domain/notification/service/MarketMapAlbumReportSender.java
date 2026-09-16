package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.dto.TopCategoryItem;
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

    private static final MarketQuery MAP_MARKETS = MarketQuery.ALL_STOCK;

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
        // 캡처 대상은 항상 두 마켓 고정이다. SectorTelegramReportSender처럼 랭킹 조회 결과로 마켓을
        // 고르면 안 된다 — 섹터 페이지는 그 랭킹이 곧 화면이라 같은 소스지만, 맵 페이지는
        // sector_price_snapshot으로 그려져서 등락률 수집만 실패한 tick에도 멀쩡히 나온다. 그때 조회
        // 결과를 따르면 캡처를 한 장도 안 한 채 "캡처 실패"로 에스컬레이션한다.
        List<byte[]> images = capture(MAP_MARKETS.toMarkets());
        if (images.isEmpty()) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED);
        }

        sendImages(images, buildCaption(dataTime, sectorAvailable));
        log.info("맵 리포트 발송 완료: 이미지={}장", images.size());
    }

    /** 캡션을 못 만들면 null — TelegramClient가 null/공백 캡션을 붙이지 않는다. */
    private String buildCaption(LocalDateTime dataTime, boolean sectorAvailable) {
        if (!sectorAvailable) {
            return null;
        }
        List<TopCategoryItem> topCategories = marketMapQueryService.getMergedTopCategoryRanking(MAP_MARKETS, dataTime);
        // sectorAvailable이 true면 그 시각 스냅샷이 있으니 보통은 안 비지만, 비면 헤더만 덜렁 남는다.
        // 이미지는 이미 찍었으므로 캡션만 버리고 보낸다.
        if (topCategories.isEmpty()) {
            log.warn("{} 시각 병합 카테고리 랭킹이 비어 있어 맵 캡션 없이 발송", dataTime);
            return null;
        }
        return categoryRankingTextBuilder.buildMapCaption(topCategories);
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
