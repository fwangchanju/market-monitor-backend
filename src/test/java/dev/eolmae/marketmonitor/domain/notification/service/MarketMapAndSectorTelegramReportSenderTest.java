package dev.eolmae.marketmonitor.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class MarketMapAndSectorTelegramReportSenderTest {

    private final ScreenshotClient screenshotClient = Mockito.mock(ScreenshotClient.class);
    private final TelegramClient telegramClient = Mockito.mock(TelegramClient.class);
    private final TelegramProperties telegramProperties =
            new TelegramProperties("token", "chat-id", "dev-chat", 10, 30);
    private final CategoryRankingTextBuilder categoryRankingTextBuilder =
            Mockito.mock(CategoryRankingTextBuilder.class);
    private final MarketMapAndSectorTelegramReportSender sender = new MarketMapAndSectorTelegramReportSender(
            screenshotClient, telegramClient, telegramProperties, categoryRankingTextBuilder);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 15, 0);
    private final byte[] mapImage = {1};
    private final byte[] sectorImage = {2};

    @Test
    void send_섹터가_가능하면_맵과_섹터_이미지를_함께_보낸다() {
        when(screenshotClient.capture("/market-map?market=KOSPI", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of(mapImage));
        when(screenshotClient.capture(
                        "/category-change-rate?market=KOSPI", "[data-captureid='category-change-rate-capture']"))
                .thenReturn(List.of(sectorImage));
        when(categoryRankingTextBuilder.buildRankingText(dataTime, MarketQuery.KOSPI))
                .thenReturn("#코스피 +1.23%\n...");

        sender.send(dataTime, Market.KOSPI, true);

        ArgumentCaptor<List<byte[]>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(telegramClient)
                .sendMediaGroup(Mockito.eq("chat-id"), imagesCaptor.capture(), Mockito.eq("#코스피 +1.23%\n..."));
        assertThat(imagesCaptor.getValue()).containsExactly(mapImage, sectorImage);
    }

    @Test
    void send_섹터가_불가능하면_맵_이미지만_보내고_캡션에_실패_안내를_덧붙인다() {
        when(screenshotClient.capture("/market-map?market=KOSDAQ", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of(mapImage));
        when(categoryRankingTextBuilder.buildRankingText(dataTime, MarketQuery.KOSDAQ))
                .thenReturn("#코스닥 -0.50%\n...");

        sender.send(dataTime, Market.KOSDAQ, false);

        verify(screenshotClient, never()).capture(Mockito.contains("/category-change-rate"), Mockito.any());
        ArgumentCaptor<List<byte[]>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMediaGroup(Mockito.eq("chat-id"), imagesCaptor.capture(), textCaptor.capture());
        assertThat(imagesCaptor.getValue()).containsExactly(mapImage);
        assertThat(textCaptor.getValue()).isEqualTo("#코스닥 -0.50%\n...\n\n섹터 이미지 생성에 실패했습니다");
    }
}
