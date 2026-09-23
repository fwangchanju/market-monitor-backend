package dev.eolmae.marketmonitor.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.dto.TopCategoryItem;
import dev.eolmae.marketmonitor.domain.view.enums.AverageMode;
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
    private static final String KOSPI_MAP_PATH = "/map/kospi?avgMode=simple&sectorFilter=true";
    private static final String KOSDAQ_MAP_PATH = "/map/kosdaq?avgMode=simple&sectorFilter=true";
    private static final String MAP_SELECTOR = "[data-captureid='market-map-capture']";

    private final ScreenshotClient screenshotClient = Mockito.mock(ScreenshotClient.class);
    private final TelegramClient telegramClient = Mockito.mock(TelegramClient.class);
    private final TelegramProperties telegramProperties = new TelegramProperties(
            "token", "chat-id", "dev-chat", 10, 15, 15, AverageMode.SIMPLE, true, MAP_SEND_TIMES);
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
    void send_두_마켓을_앨범으로_묶어_보낸다() {
        captureReturns(KOSPI_MAP_PATH, kospiImage);
        captureReturns(KOSDAQ_MAP_PATH, kosdaqImage);
        rankingReturns(topCategories);
        when(categoryRankingTextBuilder.buildMapCaption(topCategories)).thenReturn("[#코스피 / #코스닥 섹터 등락률]\n...");

        sender.send(dataTime);

        verify(telegramClient, never()).sendPhoto(Mockito.any(), Mockito.any(), Mockito.any());
        verify(telegramClient).sendMediaGroup("chat-id", List.of(kospiImage, kosdaqImage), "[#코스피 / #코스닥 섹터 등락률]\n...");
    }

    @Test
    void send_한_마켓만_캡처되면_sendPhoto로_보낸다() {
        captureReturns(KOSPI_MAP_PATH, kospiImage);
        when(screenshotClient.capture(KOSDAQ_MAP_PATH, MAP_SELECTOR)).thenReturn(List.of());
        rankingReturns(topCategories);
        when(categoryRankingTextBuilder.buildMapCaption(topCategories)).thenReturn("[#코스피 / #코스닥 섹터 등락률]\n...");

        sender.send(dataTime);

        verify(telegramClient, never()).sendMediaGroup(Mockito.any(), Mockito.any(), Mockito.any());
        verify(telegramClient).sendPhoto("chat-id", kospiImage, "[#코스피 / #코스닥 섹터 등락률]\n...");
    }

    @Test
    void send_캡처가_통째로_비면_에스컬레이션한다() {
        when(screenshotClient.capture(KOSPI_MAP_PATH, MAP_SELECTOR)).thenReturn(List.of());
        when(screenshotClient.capture(KOSDAQ_MAP_PATH, MAP_SELECTOR)).thenReturn(List.of());

        assertThatThrownBy(() -> sender.send(dataTime)).isInstanceOf(EscalateException.class);

        verifyNoInteractions(telegramClient);
    }

    /**
     * 캡처 대상 마켓을 랭킹 조회 결과로 정하면 이 상황에서 한 장도 안 찍고 에스컬레이션한다 — 맵 페이지는
     * sector_price_snapshot으로 그려져서 등락률 수집만 실패해도 멀쩡히 나오기 때문이다. 그 시각 데이터가
     * 없거나 요청 마켓 중 하나라도 합산이 비어 랭킹이 비는 경우, 이미지는 그대로 보내고 캡션만 뺀다.
     */
    @Test
    void send_병합_랭킹이_비어도_이미지는_보낸다() {
        captureReturns(KOSPI_MAP_PATH, kospiImage);
        captureReturns(KOSDAQ_MAP_PATH, kosdaqImage);
        rankingReturns(List.of());

        sender.send(dataTime);

        verify(telegramClient).sendMediaGroup("chat-id", List.of(kospiImage, kosdaqImage), null);
        verify(categoryRankingTextBuilder, never()).buildMapCaption(Mockito.any());
    }

    // 프로퍼티 값이 캡처 URL과 캡션 계산 양쪽에 같은 값으로 들어가는지 — 기본값(SIMPLE/true)이 아닌
    // 값(WEIGHTED/false)으로 바꿔도 둘이 같이 바뀌어야 한다.
    @Test
    void send_프로퍼티가_WEIGHTED와_sectorFilter_false면_맵_캡처_URL과_캡션_계산에_그대로_반영된다() {
        TelegramProperties weightedProperties = new TelegramProperties(
                "token", "chat-id", "dev-chat", 10, 15, 15, AverageMode.WEIGHTED, false, MAP_SEND_TIMES);
        MarketMapAlbumReportSender weightedSender = new MarketMapAlbumReportSender(
                screenshotClient,
                telegramClient,
                weightedProperties,
                categoryRankingTextBuilder,
                marketMapQueryService);
        String kospiWeightedPath = "/map/kospi?avgMode=weighted&sectorFilter=false";
        String kosdaqWeightedPath = "/map/kosdaq?avgMode=weighted&sectorFilter=false";
        when(screenshotClient.capture(kospiWeightedPath, MAP_SELECTOR)).thenReturn(List.of(kospiImage));
        when(screenshotClient.capture(kosdaqWeightedPath, MAP_SELECTOR)).thenReturn(List.of(kosdaqImage));
        when(marketMapQueryService.getMergedTopCategoryRanking(
                        MarketQuery.ALL_STOCK, dataTime, AverageMode.WEIGHTED, false))
                .thenReturn(topCategories);
        when(categoryRankingTextBuilder.buildMapCaption(topCategories)).thenReturn("[#코스피 / #코스닥 섹터 등락률]\n...");

        weightedSender.send(dataTime);

        verify(telegramClient).sendMediaGroup("chat-id", List.of(kospiImage, kosdaqImage), "[#코스피 / #코스닥 섹터 등락률]\n...");
    }

    private void captureReturns(String path, byte[] image) {
        when(screenshotClient.capture(path, MAP_SELECTOR)).thenReturn(List.of(image));
    }

    private void rankingReturns(List<TopCategoryItem> items) {
        when(marketMapQueryService.getMergedTopCategoryRanking(
                        MarketQuery.ALL_STOCK, dataTime, AverageMode.SIMPLE, true))
                .thenReturn(items);
    }
}
