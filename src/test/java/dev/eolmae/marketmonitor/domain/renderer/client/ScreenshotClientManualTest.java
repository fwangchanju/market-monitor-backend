package dev.eolmae.marketmonitor.domain.renderer.client;

import static org.assertj.core.api.Assertions.assertThat;

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
 * MARKET_MAP을 대상으로 한다 — CollectionScheduler가 실제로 캡처하는 것과 같은 대상(스케줄러는
 * MarketMapAndSectorTelegramReportSender를 통해 MARKET_MAP/CATEGORY_CHANGE_RATE만 캡처한다).
 * MARKET_SUMMARY는 프론트(WidgetSection)에 data-captureid만 있고 data-capture-ready가 없어서
 * 항상 15초 타임아웃으로 실패하는 미완성 대상이라 쓰지 않는다.
 *
 * 실행 조건: RENDERER_URL 환경변수(없으면 기본값 http://market-monitor-renderer:3000 사용) —
 * 렌더러 컨테이너가 그 주소로 실제 접근 가능해야 한다.
 * 실행 명령: ./gradlew manualTest --tests "*.ScreenshotClientManualTest" -i
 */
@Tag("manual")
class ScreenshotClientManualTest {

    @Test
    void capturesNonEmptyImages() {
        String rendererUrl = System.getenv().getOrDefault("RENDERER_URL", "http://market-monitor-renderer:3000");
        var client = new ScreenshotClient(new RendererProperties(rendererUrl), RestClient.create());

        assertThat(client.capture(RenderTarget.MARKET_MAP.path() + "?market=KOSPI", RenderTarget.MARKET_MAP.selector()))
                .isNotEmpty();
    }
}
