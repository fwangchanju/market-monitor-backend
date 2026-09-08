package dev.eolmae.marketmonitor.domain.renderer.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.renderer.properties.RendererProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 렌더러 컨테이너가 실제로 스크린샷을 캡처하는지 확인하는 수동 검증용 테스트.
 * Spring 컨텍스트 없이 ScreenshotClient만 직접 구성해서 실제 RENDERER_URL에 네트워크로 접근하므로,
 * 배포 후 렌더러가 정상 동작하는지 확인할 때만 실행한다.
 *
 * **스케줄러(CollectionScheduler)가 실제로 캡처하는 것과 정확히 같은 형상만 확인한다** — 그 외의
 * 대상은 이 테스트의 목적이 아니라서 의도적으로 뺐다. 실제 발송 흐름은
 * DailyMarketReportSender.send() → MarketMapAndSectorTelegramReportSender.send()이고, 거기서
 * KOSPI/KOSDAQ 각각에 대해 맵(MARKET_MAP) 캡처 + 섹터(CATEGORY_CHANGE_RATE) 캡처를 한다 — 그래서
 * 이 테스트도 두 마켓 × 두 대상, 총 4번을 그대로 재현한다. MARKET_SUMMARY는 그 발송 흐름에서 아예
 * 안 쓰이는 대상이라(프론트 WidgetSection에 data-capture-ready도 없어서 항상 타임아웃 나는, 완성
 * 안 된 기능) 여기서 다루지 않는다. 텔레그램 실제 전송까지 확인하려면 TelegramReportCycleManualTest를
 * 쓴다 — 이 테스트는 렌더러 캡처 단계만 텔레그램 발송 없이 격리해서 빠르게 확인하는 용도다.
 *
 * 실행 조건: RENDERER_URL 환경변수(없으면 기본값 http://market-monitor-renderer:3000 사용) —
 * 렌더러 컨테이너가 그 주소로 실제 접근 가능해야 한다.
 * 실행 명령: ./gradlew manualTest --tests "*.ScreenshotClientManualTest" -i
 */
@Tag("manual")
class ScreenshotClientManualTest {

    @Test
    void capturesNonEmptyImagesForEveryMarketAndTarget() {
        String rendererUrl = System.getenv().getOrDefault("RENDERER_URL", "http://market-monitor-renderer:3000");
        var client = new ScreenshotClient(new RendererProperties(rendererUrl), RestClient.create());

        for (Market market : Market.values()) {
            assertThat(client.capture(
                            RenderTarget.MARKET_MAP.path() + "?market=" + market.name(),
                            RenderTarget.MARKET_MAP.selector()))
                    .as("%s 마켓맵 캡처", market)
                    .isNotEmpty();
            assertThat(client.capture(
                            RenderTarget.CATEGORY_CHANGE_RATE.path() + "?market=" + market.name(),
                            RenderTarget.CATEGORY_CHANGE_RATE.selector()))
                    .as("%s 섹터 캡처", market)
                    .isNotEmpty();
        }
    }
}
