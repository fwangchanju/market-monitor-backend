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
public class MarketMapCategoryRankingTelegramReportSender extends TelegramReportSender {

    private final CategoryRankingTextBuilder categoryRankingTextBuilder;

    public MarketMapCategoryRankingTelegramReportSender(
            CategoryRankingTextBuilder categoryRankingTextBuilder,
            ScreenshotClient screenshotClient,
            TelegramClient telegramClient,
            TelegramProperties telegramProperties) {
        super(screenshotClient, telegramClient, telegramProperties);
        this.categoryRankingTextBuilder = categoryRankingTextBuilder;
    }

    @Override
    protected RenderTarget target() {
        return RenderTarget.CATEGORY_CHANGE_RATE;
    }

    // 지도와 동일하게 마켓을 하나로 합치지 않고 마켓별로 각각 캡처한다(ALL_STOCKS면 KOSPI/KOSDAQ 이미지 2장).
    @Override
    protected List<String> captureQueryValues(MarketQuery query) {
        return query.toMarkets().stream().map(Market::name).toList();
    }

    @Override
    protected String buildText(LocalDateTime dataTime, MarketQuery query) {
        return "Custom Sector\n" + categoryRankingTextBuilder.buildRankingText(dataTime, query);
    }
}
