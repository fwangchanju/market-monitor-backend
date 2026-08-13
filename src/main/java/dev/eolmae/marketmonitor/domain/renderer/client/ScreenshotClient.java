package dev.eolmae.marketmonitor.domain.renderer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.renderer.properties.RendererProperties;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenshotClient {

    private final RendererProperties properties;
    private final RestClient restClient;

    // path: 프론트 base 뒤에 붙일 경로(쿼리 포함 가능), selector: 캡처할 요소들의 CSS 셀렉터
    public List<byte[]> capture(String path, String selector) {
        return Optional.ofNullable(requestCapture(path, selector))
                .map(CaptureResponse::images)
                .orElseThrow(() -> new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED))
                .stream()
                .map(img -> Base64.getDecoder().decode(img.data()))
                .toList();
    }

    private CaptureResponse requestCapture(String path, String selector) {
        try {
            return restClient
                    .post()
                    .uri(properties.url() + "/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CaptureRequest(path, selector))
                    .retrieve()
                    .body(CaptureResponse.class);
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED, e);
        }
    }

    private record CaptureRequest(
            @JsonProperty("path") String path,
            @JsonProperty("selector") String selector) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CaptureResponse(List<ImageData> images) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ImageData(String name, String data) {}
    }
}
