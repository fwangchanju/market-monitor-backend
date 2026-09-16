package dev.eolmae.marketmonitor.domain.notification.service;

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
import dev.eolmae.marketmonitor.domain.view.dto.MergedTopCategoryRanking;
import dev.eolmae.marketmonitor.domain.view.dto.TopCategoryItem;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MarketMapAlbumReportSenderTest {

    private static final List<LocalTime> MAP_SEND_TIMES = List.of(LocalTime.of(8, 15));

    private final ScreenshotClient screenshotClient = Mockito.mock(ScreenshotClient.class);
    private final TelegramClient telegramClient = Mockito.mock(TelegramClient.class);
    private final TelegramProperties telegramProperties =
            new TelegramProperties("token", "chat-id", "dev-chat", 10, 15, 15, MAP_SEND_TIMES);
    private final CategoryRankingTextBuilder categoryRankingTextBuilder =
            Mockito.mock(CategoryRankingTextBuilder.class);
    private final MarketMapQueryService marketMapQueryService = Mockito.mock(MarketMapQueryService.class);
    private final MarketMapAlbumReportSender sender = new MarketMapAlbumReportSender(
            screenshotClient, telegramClient, telegramProperties, categoryRankingTextBuilder, marketMapQueryService);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 8, 15);
    private final byte[] kospiImage = {1};
    private final byte[] kosdaqImage = {2};
    private final List<TopCategoryItem> topCategories = List.of(new TopCategoryItem("반도체", BigDecimal.valueOf(1.35)));

    @Test
    void send_두_마켓_다_있으면_앨범으로_묶어_보낸다() {
        when(marketMapQueryService.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, dataTime))
                .thenReturn(new MergedTopCategoryRanking(List.of(Market.KOSPI, Market.KOSDAQ), topCategories));
        when(screenshotClient.capture("/market-map?market=KOSPI", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of(kospiImage));
        when(screenshotClient.capture("/market-map?market=KOSDAQ", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of(kosdaqImage));
        when(categoryRankingTextBuilder.buildMapCaption(topCategories)).thenReturn("[#코스피 / #코스닥 섹터 등락률]\n...");

        sender.send(dataTime, true);

        verify(telegramClient, never()).sendPhoto(Mockito.any(), Mockito.any(), Mockito.any());
        verify(telegramClient).sendMediaGroup("chat-id", List.of(kospiImage, kosdaqImage), "[#코스피 / #코스닥 섹터 등락률]\n...");
    }

    @Test
    void send_한_마켓만_있으면_sendPhoto로_보낸다() {
        when(marketMapQueryService.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, dataTime))
                .thenReturn(new MergedTopCategoryRanking(List.of(Market.KOSPI), topCategories));
        when(screenshotClient.capture("/market-map?market=KOSPI", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of(kospiImage));
        when(categoryRankingTextBuilder.buildMapCaption(topCategories)).thenReturn("[#코스피 / #코스닥 섹터 등락률]\n...");

        sender.send(dataTime, true);

        verify(telegramClient, never()).sendMediaGroup(Mockito.any(), Mockito.any(), Mockito.any());
        verify(telegramClient).sendPhoto("chat-id", kospiImage, "[#코스피 / #코스닥 섹터 등락률]\n...");
    }

    @Test
    void send_캡처가_통째로_비면_에스컬레이션한다() {
        when(marketMapQueryService.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, dataTime))
                .thenReturn(new MergedTopCategoryRanking(List.of(Market.KOSPI, Market.KOSDAQ), topCategories));
        when(screenshotClient.capture("/market-map?market=KOSPI", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of());
        when(screenshotClient.capture("/market-map?market=KOSDAQ", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> sender.send(dataTime, true)).isInstanceOf(EscalateException.class);

        verifyNoInteractions(telegramClient);
    }

    // 카테고리 등락률 수집이 실패해도(sectorAvailable=false) 맵 이미지는 그와 무관하므로 그대로 보낸다.
    // 다만 캡션(카테고리 등락률 기반)은 못 만드므로 캡션 없이 보낸다.
    @Test
    void send_sectorAvailable이_false면_캡션_없이_이미지만_보낸다() {
        when(marketMapQueryService.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, dataTime))
                .thenReturn(new MergedTopCategoryRanking(List.of(Market.KOSPI, Market.KOSDAQ), topCategories));
        when(screenshotClient.capture("/market-map?market=KOSPI", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of(kospiImage));
        when(screenshotClient.capture("/market-map?market=KOSDAQ", "[data-captureid='market-map-capture']"))
                .thenReturn(List.of(kosdaqImage));

        sender.send(dataTime, false);

        verify(telegramClient).sendMediaGroup("chat-id", List.of(kospiImage, kosdaqImage), null);
        verifyNoInteractions(categoryRankingTextBuilder);
    }
}
