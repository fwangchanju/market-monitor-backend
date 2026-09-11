package dev.eolmae.marketmonitor.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryRankingSummary;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SectorTelegramReportSenderTest {

    private static final int BEFORE_MINUTES = 15;

    private final ScreenshotClient screenshotClient = Mockito.mock(ScreenshotClient.class);
    private final TelegramClient telegramClient = Mockito.mock(TelegramClient.class);
    private final TelegramProperties telegramProperties =
            new TelegramProperties("token", "chat-id", "dev-chat", 10, BEFORE_MINUTES, 120, BEFORE_MINUTES);
    private final CategoryRankingTextBuilder categoryRankingTextBuilder =
            Mockito.mock(CategoryRankingTextBuilder.class);
    private final MarketMapQueryService marketMapQueryService = Mockito.mock(MarketMapQueryService.class);
    private final SectorTelegramReportSender sender = new SectorTelegramReportSender(
            screenshotClient, telegramClient, telegramProperties, categoryRankingTextBuilder, marketMapQueryService);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 8, 25);
    private final byte[] kospiImage = {1};
    private final byte[] kosdaqImage = {2};

    @Test
    void send_섹터_스냅샷이_없으면_캡처도_발송도_하지_않는다() {
        sender.send(dataTime, false);

        verifyNoInteractions(marketMapQueryService);
        verifyNoInteractions(screenshotClient);
        verifyNoInteractions(telegramClient);
    }

    @Test
    void send_랭킹_결과가_통째로_비면_캡처도_발송도_하지_않는다() {
        when(marketMapQueryService.getTopCategoryRankings(MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES))
                .thenReturn(List.of());

        sender.send(dataTime, true);

        verifyNoInteractions(screenshotClient);
        verifyNoInteractions(telegramClient);
    }

    @Test
    void send_두_마켓_다_있으면_섹터_2장을_sendMediaGroup_하나로_보낸다() {
        List<CategoryRankingSummary> rankings = List.of(
                new CategoryRankingSummary(Market.KOSPI, BigDecimal.valueOf(1.23), List.of()),
                new CategoryRankingSummary(Market.KOSDAQ, BigDecimal.valueOf(-0.5), List.of()));
        when(marketMapQueryService.getTopCategoryRankings(MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES))
                .thenReturn(rankings);
        when(screenshotClient.capture(
                        "/category-change-rate?market=KOSPI&beforeMinutes=15",
                        "[data-captureid='category-change-rate-capture']"))
                .thenReturn(List.of(kospiImage));
        when(screenshotClient.capture(
                        "/category-change-rate?market=KOSDAQ&beforeMinutes=15",
                        "[data-captureid='category-change-rate-capture']"))
                .thenReturn(List.of(kosdaqImage));
        when(categoryRankingTextBuilder.buildRankingText(rankings)).thenReturn("#코스피 +1.23%\n...\n\n#코스닥 -0.50%\n...");

        sender.send(dataTime, true);

        verify(telegramClient, never()).sendPhoto(Mockito.any(), Mockito.any(), Mockito.any());
        ArgumentCaptor<List<byte[]>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(telegramClient)
                .sendMediaGroup(
                        Mockito.eq("chat-id"),
                        imagesCaptor.capture(),
                        Mockito.eq("#코스피 +1.23%\n...\n\n#코스닥 -0.50%\n..."));
        assertThat(imagesCaptor.getValue()).containsExactly(kospiImage, kosdaqImage);
    }

    @Test
    void send_한_마켓만_있으면_그_마켓만_캡처해서_sendPhoto로_보낸다() {
        List<CategoryRankingSummary> rankings =
                List.of(new CategoryRankingSummary(Market.KOSPI, BigDecimal.valueOf(1.23), List.of()));
        when(marketMapQueryService.getTopCategoryRankings(MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES))
                .thenReturn(rankings);
        when(screenshotClient.capture(
                        "/category-change-rate?market=KOSPI&beforeMinutes=15",
                        "[data-captureid='category-change-rate-capture']"))
                .thenReturn(List.of(kospiImage));
        when(categoryRankingTextBuilder.buildRankingText(rankings)).thenReturn("#코스피 +1.23%\n...");

        sender.send(dataTime, true);

        verify(screenshotClient, never()).capture(Mockito.contains("KOSDAQ"), Mockito.any());
        verify(telegramClient, never()).sendMediaGroup(Mockito.any(), Mockito.any(), Mockito.any());
        verify(telegramClient).sendPhoto(Mockito.eq("chat-id"), Mockito.eq(kospiImage), Mockito.eq("#코스피 +1.23%\n..."));
    }
}
