package dev.eolmae.marketmonitor.domain.renderer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "renderer.enabled", havingValue = "true")
public class ScreenshotClient {

    @Value("${renderer.url}")
    private String rendererUrl;

    private final RestClient restClient = RestClient.create();

    public List<byte[]> captureAll() {
        try {
            CaptureResponse response = restClient
                    .post()
                    .uri(rendererUrl + "/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CaptureResponse.class);

            if (response == null || response.images() == null) {
                log.warn("renderer 응답이 비어 있음");
                return List.of();
            }

            return response.images().stream()
                    .map(img -> Base64.getDecoder().decode(img.data()))
                    .toList();
        } catch (Exception e) {
            log.error("스크린샷 캡처 실패: {}", e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CaptureResponse(List<ImageData> images) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ImageData(String name, String data) {}
    }
}
