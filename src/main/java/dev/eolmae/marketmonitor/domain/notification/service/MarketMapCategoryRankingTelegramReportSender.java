package dev.eolmae.marketmonitor.domain.notification.service;

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

    // 화면(CategoryChangeRatePage)은 market=ALL_STOCKS 하나로도 KOSPI/KOSDAQ 그래프를 한 페이지에 같이
    // 그려주므로, 마켓별로 나눠 캡처하지 않고 요청받은 query 값 그대로 하나만 찍는다(이미지 1장).
    @Override
    protected List<String> captureQueryValues(MarketQuery query) {
        return List.of(query.name());
    }

    @Override
    protected String buildText(LocalDateTime dataTime, MarketQuery query) {
        return "Custom Sector\n" + categoryRankingTextBuilder.buildRankingText(dataTime, query);
    }
}
