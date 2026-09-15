package dev.eolmae.marketmonitor.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
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
            new TelegramProperties("token", "chat-id", "dev-chat", 10, BEFORE_MINUTES, BEFORE_MINUTES);
    private final CategoryRankingTextBuilder categoryRankingTextBuilder =
            Mockito.mock(CategoryRankingTextBuilder.class);
    private final MarketMapQueryService marketMapQueryService = Mockito.mock(MarketMapQueryService.class);
    private final SectorTelegramReportSender sender = new SectorTelegramReportSender(
            screenshotClient, telegramClient, telegramProperties, categoryRankingTextBuilder, marketMapQueryService);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 8, 25);
    private final byte[] kospiImage = {1};
    private final byte[] kosdaqImage = {2};
    private final BigDecimal indexChangeRate = BigDecimal.valueOf(1.23);

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
    void send_두_마켓_다_있으면_마켓별로_각각_sendPhoto_한다() {
        CategoryRankingSummary kospiSummary = new CategoryRankingSummary(Market.KOSPI, indexChangeRate, List.of());
        CategoryRankingSummary kosdaqSummary = new CategoryRankingSummary(Market.KOSDAQ, indexChangeRate, List.of());
        List<CategoryRankingSummary> rankings = List.of(kospiSummary, kosdaqSummary);
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
        when(categoryRankingTextBuilder.buildSectorCaption(kospiSummary, BEFORE_MINUTES))
                .thenReturn("[#코스피 15분 전 대비]\n...");
        when(categoryRankingTextBuilder.buildSectorCaption(kosdaqSummary, BEFORE_MINUTES))
                .thenReturn("[#코스닥 15분 전 대비]\n...");

        sender.send(dataTime, true);

        verify(telegramClient, never()).sendMediaGroup(Mockito.any(), Mockito.any(), Mockito.any());
        ArgumentCaptor<byte[]> imageCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> captionCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, Mockito.times(2))
                .sendPhoto(Mockito.eq("chat-id"), imageCaptor.capture(), captionCaptor.capture());
        assertThat(imageCaptor.getAllValues()).containsExactly(kospiImage, kosdaqImage);
        assertThat(captionCaptor.getAllValues()).containsExactly("[#코스피 15분 전 대비]\n...", "[#코스닥 15분 전 대비]\n...");
    }

    @Test
    void send_한_마켓만_있으면_그_마켓만_캡처해서_보낸다() {
        CategoryRankingSummary kospiSummary = new CategoryRankingSummary(Market.KOSPI, indexChangeRate, List.of());
        List<CategoryRankingSummary> rankings = List.of(kospiSummary);
        when(marketMapQueryService.getTopCategoryRankings(MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES))
                .thenReturn(rankings);
        when(screenshotClient.capture(
                        "/category-change-rate?market=KOSPI&beforeMinutes=15",
                        "[data-captureid='category-change-rate-capture']"))
                .thenReturn(List.of(kospiImage));
        when(categoryRankingTextBuilder.buildSectorCaption(kospiSummary, BEFORE_MINUTES))
                .thenReturn("[#코스피 15분 전 대비]\n...");

        sender.send(dataTime, true);

        verify(screenshotClient, never()).capture(Mockito.contains("KOSDAQ"), Mockito.any());
        verify(telegramClient)
                .sendPhoto(Mockito.eq("chat-id"), Mockito.eq(kospiImage), Mockito.eq("[#코스피 15분 전 대비]\n..."));
        verify(telegramClient, Mockito.times(1)).sendPhoto(Mockito.any(), Mockito.any(), Mockito.any());
    }

    // 캡처가 통째로 비는 것은 마켓별 분리 이전에는 텔레그램 400으로 드러나던 상황이다. 지금은 발송
    // 루프가 0회 돌 뿐이라, 이 예외가 없으면 아무 흔적 없이 그 tick이 사라진다.
    @Test
    void send_캡처가_통째로_비면_에스컬레이션한다() {
        CategoryRankingSummary kospiSummary = new CategoryRankingSummary(Market.KOSPI, indexChangeRate, List.of());
        when(marketMapQueryService.getTopCategoryRankings(MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES))
                .thenReturn(List.of(kospiSummary));
        when(screenshotClient.capture(
                        "/category-change-rate?market=KOSPI&beforeMinutes=15",
                        "[data-captureid='category-change-rate-capture']"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> sender.send(dataTime, true)).isInstanceOf(EscalateException.class);

        verifyNoInteractions(telegramClient);
    }

    @Test
    void send_한_마켓_캡처만_비면_나머지_마켓은_그대로_보낸다() {
        CategoryRankingSummary kospiSummary = new CategoryRankingSummary(Market.KOSPI, indexChangeRate, List.of());
        CategoryRankingSummary kosdaqSummary = new CategoryRankingSummary(Market.KOSDAQ, indexChangeRate, List.of());
        when(marketMapQueryService.getTopCategoryRankings(MarketQuery.ALL_STOCK, dataTime, BEFORE_MINUTES))
                .thenReturn(List.of(kospiSummary, kosdaqSummary));
        when(screenshotClient.capture(
                        "/category-change-rate?market=KOSPI&beforeMinutes=15",
                        "[data-captureid='category-change-rate-capture']"))
                .thenReturn(List.of());
        when(screenshotClient.capture(
                        "/category-change-rate?market=KOSDAQ&beforeMinutes=15",
                        "[data-captureid='category-change-rate-capture']"))
                .thenReturn(List.of(kosdaqImage));
        when(categoryRankingTextBuilder.buildSectorCaption(kosdaqSummary, BEFORE_MINUTES))
                .thenReturn("[#코스닥 15분 전 대비]\n...");

        sender.send(dataTime, true);

        verify(telegramClient)
                .sendPhoto(Mockito.eq("chat-id"), Mockito.eq(kosdaqImage), Mockito.eq("[#코스닥 15분 전 대비]\n..."));
        verify(telegramClient, Mockito.times(1)).sendPhoto(Mockito.any(), Mockito.any(), Mockito.any());
    }
}
