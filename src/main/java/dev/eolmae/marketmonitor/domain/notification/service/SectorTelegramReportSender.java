package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryRankingSummary;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 두 마켓(KOSPI/KOSDAQ)의 섹터(카테고리 등락률) 이미지만 한 메시지로 묶어 보낸다 — 맵을 포함하지 않는
 * tick 전용. {@link TelegramReportSender}를 상속하지 않는 독립 컴포넌트다. 그 부모의 send()는 캡처
 * URL을 "?market=" 값만 붙여 만들고 "&beforeMinutes="를 붙이지 않으므로, 상속하면 부모 send()를
 * 통째로 오버라이드해야 해서 상속으로 얻는 것이 없다. 더 나쁜 것은 오버라이드를 빠뜨렸을 때 컴파일도
 * 테스트도 안 깨진 채 이미지가 프론트 기본값(30분) 기준으로 나간다는 점이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SectorTelegramReportSender {

    private final ScreenshotClient screenshotClient;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final CategoryRankingTextBuilder categoryRankingTextBuilder;
    private final MarketMapQueryService marketMapQueryService;

    public void send(LocalDateTime dataTime, boolean sectorAvailable) {
        // 아래 "조회가 비어 있어"와 결과는 비슷해 보여도 성격이 다르다 — 이쪽은 수집기가 예외를 던져
        // CollectionScheduler.run()이 이미 개발자 에스컬레이션을 보낸 뒤다. 아래쪽은 아무 알림도 안 나간
        // 조용한 구멍이라 그 WARN이 유일한 흔적이다. 로그를 뒤질 때 둘을 구분할 수 있어야 한다.
        if (!sectorAvailable) {
            log.warn("{} 시각 카테고리 등락률 수집이 실패해서 섹터 발송을 건너뜀", dataTime);
            return;
        }

        int beforeMinutes = telegramProperties.beforeMinutes();
        List<CategoryRankingSummary> rankings =
                marketMapQueryService.getTopCategoryRankings(MarketQuery.ALL_STOCK, dataTime, beforeMinutes);
        if (rankings.isEmpty()) {
            log.warn("{} 시각 카테고리 등락률 랭킹 조회가 비어 있어 섹터 발송을 건너뜀", dataTime);
            return;
        }

        // 결과에 있는 마켓만 캡처한다 — getTopCategoryRankings가 그 시각 데이터 없는 마켓을 이미 결과에서
        // 뺀다. 목록을 상수로 박아두면 데이터 없는 마켓의 빈 화면이 앨범에 섞인다.
        List<byte[]> images = rankings.stream()
                .flatMap(summary -> screenshotClient
                        .capture(
                                sectorPath(summary.market(), beforeMinutes),
                                RenderTarget.CATEGORY_CHANGE_RATE.selector())
                        .stream())
                .toList();
        String text = categoryRankingTextBuilder.buildRankingText(rankings);

        // 텔레그램 sendMediaGroup은 media가 2~10개여야 한다. 한 마켓만 수집에 성공해서 랭킹에 한 마켓만
        // 남으면(이 경로에서 새로 생기는 상황) 1장짜리 앨범이 되어 400으로 실패하므로 sendPhoto로 보낸다.
        if (images.size() == 1) {
            telegramClient.sendPhoto(telegramProperties.chatId(), images.get(0), text);
        } else {
            telegramClient.sendMediaGroup(telegramProperties.chatId(), images, text);
        }
        log.info("섹터 리포트 발송 완료: 이미지={}장", images.size());
    }

    private String sectorPath(Market market, int beforeMinutes) {
        return RenderTarget.CATEGORY_CHANGE_RATE.path() + "?market=" + market.name() + "&beforeMinutes="
                + beforeMinutes;
    }
}
