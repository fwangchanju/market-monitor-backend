package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.domain.access.properties.AdminProperties;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.MarketMonitorProperties;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.renderer.properties.RendererProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 시장현황/마켓맵 이미지 렌더링 + 텔레그램 발송이 실제로 동작하는지 확인하는 수동 검증용 테스트.
 * Spring 컨텍스트 없이 필요한 컴포넌트만 직접 구성해서 실제 renderer/텔레그램 API에 네트워크로 접근하므로,
 * 배포 후 정상 동작하는지 확인할 때만 --tests로 직접 지정해서 실행한다.
 */
class RenderServiceManualTest {

    private final String rendererUrl =
            System.getenv().getOrDefault("RENDERER_URL", "http://market-monitor-renderer:3000");
    private final ScreenshotClient screenshotClient =
            new ScreenshotClient(new RendererProperties(rendererUrl), RestClient.create());

    private final TelegramProperties telegramProperties = new TelegramProperties(
            System.getenv("TELEGRAM_BOT_TOKEN"), System.getenv("TELEGRAM_CHAT_ID"), System.getenv("DEVELOPER_CHAT_ID"));
    private final TelegramClient telegramClient = new TelegramClient(telegramProperties, RestClient.create());

    private final MarketMonitorProperties marketMonitorProperties =
            new MarketMonitorProperties("https://eolmae.duckdns.org");
    private final AdminProperties adminProperties =
            new AdminProperties(List.of(System.getenv("ADMIN_TOKENS").split(",")));

    private final RenderService renderService = new RenderService(
            screenshotClient, telegramClient, telegramProperties, marketMonitorProperties, adminProperties);

    @Test
    void sendsStitchedMarketSummaryImageToTelegram() {
        renderService.send(RenderTarget.MARKET_SUMMARY);
    }

    @Test
    void sendsMarketMapImageToTelegram() {
        renderService.send(RenderTarget.MARKET_MAP);
    }
}
