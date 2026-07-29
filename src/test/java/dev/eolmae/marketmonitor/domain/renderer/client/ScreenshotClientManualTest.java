package dev.eolmae.marketmonitor.domain.renderer.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketmonitor.domain.renderer.properties.RendererProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 렌더러 컨테이너가 실제로 스크린샷을 캡처하는지 확인하는 수동 검증용 테스트.
 * Spring 컨텍스트 없이 ScreenshotClient만 직접 구성해서 실제 RENDERER_URL에 네트워크로 접근하므로,
 * 배포 후 렌더러가 정상 동작하는지 확인할 때만 --tests로 직접 지정해서 실행한다.
 */
class ScreenshotClientManualTest {

    @Test
    void capturesNonEmptyImages() {
        String rendererUrl = System.getenv().getOrDefault("RENDERER_URL", "http://market-monitor-renderer:3000");
        var client = new ScreenshotClient(new RendererProperties(rendererUrl), RestClient.create());

        assertThat(client.captureAll()).isNotEmpty();
    }
}
