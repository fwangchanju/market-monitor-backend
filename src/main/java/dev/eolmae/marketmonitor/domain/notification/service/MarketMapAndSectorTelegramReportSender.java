package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryRankingSummary;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 마켓(KOSPI 또는 KOSDAQ) 하나의 마켓맵 이미지 + 섹터(카테고리 등락률) 이미지를 한 메시지로 묶어 보낸다.
 * sectorAvailable이 false(카테고리 등락률 스냅샷 실패)면 섹터 이미지 없이 맵 이미지만 보내고, 캡션에
 * 실패 안내를 덧붙인다. sectorAvailable이 true인데도 그 시각 랭킹 조회가 비면(예외 없이 조용히 생긴
 * 구멍) 발송 자체를 건너뛴다 — 캡처된 화면(최신)과 캡션(dataTime)의 시각이 어긋나는 것을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketMapAndSectorTelegramReportSender {

    private static final String SECTOR_CAPTURE_FAILED_NOTICE = "\n\n섹터 이미지 생성에 실패했습니다";

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final CategoryRankingTextBuilder categoryRankingTextBuilder;
    private final MarketMapQueryService marketMapQueryService;

    public void send(LocalDateTime dataTime, Market market, boolean sectorAvailable) {
        int beforeMinutes = telegramProperties.beforeMinutes();
        List<CategoryRankingSummary> rankings = marketMapQueryService.getTopCategoryRankings(
                MarketQuery.valueOf(market.name()), dataTime, beforeMinutes);

        if (sectorAvailable && rankings.isEmpty()) {
            log.warn("{} 시각 {} 카테고리 등락률 스냅샷이 없어 발송을 건너뜀", dataTime, market);
            return;
        }

        List<byte[]> images =
                new ArrayList<>(screenshotClient.capture(mapPath(market), RenderTarget.MARKET_MAP.selector()));
        if (sectorAvailable) {
            images.addAll(screenshotClient.capture(
                    sectorPath(market, beforeMinutes), RenderTarget.CATEGORY_CHANGE_RATE.selector()));
        }

        String text = categoryRankingTextBuilder.buildRankingText(rankings);
        if (!sectorAvailable) {
            text += SECTOR_CAPTURE_FAILED_NOTICE;
        }

        telegramClient.sendMediaGroup(telegramProperties.chatId(), images, text);
        log.info("마켓 리포트 발송 완료: market={}, 이미지={}장, sectorAvailable={}", market, images.size(), sectorAvailable);
    }

    private String mapPath(Market market) {
        return RenderTarget.MARKET_MAP.path() + "?market=" + market.name();
    }

    private String sectorPath(Market market, int beforeMinutes) {
        return RenderTarget.CATEGORY_CHANGE_RATE.path() + "?market=" + market.name() + "&beforeMinutes="
                + beforeMinutes;
    }
}
