package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarketMapTelegramReportSender extends TelegramReportSender {

    private final CategoryRankingTextBuilder categoryRankingTextBuilder;

    public MarketMapTelegramReportSender(
            ScreenshotClient screenshotClient,
            TelegramClient telegramClient,
            TelegramProperties telegramProperties,
            CategoryRankingTextBuilder categoryRankingTextBuilder) {
        super(screenshotClient, telegramClient, telegramProperties);
        this.categoryRankingTextBuilder = categoryRankingTextBuilder;
    }

    @Override
    protected RenderTarget target() {
        return RenderTarget.MARKET_MAP;
    }

    // 지도는 마켓을 하나로 합치지 않고 마켓별로 각각 캡처한다(ALL_STOCKS면 KOSPI/KOSDAQ 이미지 2장).
    @Override
    protected List<String> captureQueryValues(MarketQuery query) {
        return query.toMarkets().stream().map(Market::name).toList();
    }

    // 섹터 이미지 발송은 비활성화돼 있지만 랭킹 텍스트 자체는 유용하므로, 맵 캡션에 그대로 이어붙인다.
    @Override
    protected String buildText(LocalDateTime dataTime, MarketQuery query) {
        return "Custom Map\n" + categoryRankingTextBuilder.buildRankingText(dataTime, query);
    }
}
