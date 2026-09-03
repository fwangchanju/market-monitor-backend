package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarketMapTelegramReportSender extends TelegramReportSender {

    public MarketMapTelegramReportSender(
            ScreenshotClient screenshotClient, TelegramClient telegramClient, TelegramProperties telegramProperties) {
        super(screenshotClient, telegramClient, telegramProperties);
    }

    @Override
    protected RenderTarget target() {
        return RenderTarget.MARKET_MAP;
    }

    @Override
    protected String buildText(LocalDateTime dataTime, List<Market> markets) {
        return "Custom Map";
    }
}
