package dev.eolmae.marketmonitor.domain.renderer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    public List<byte[]> captureAll() {
        return Optional.ofNullable(requestCapture())
                .map(CaptureResponse::images)
                .orElseThrow(() -> new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED))
                .stream()
                .map(img -> Base64.getDecoder().decode(img.data()))
                .toList();
    }

    private CaptureResponse requestCapture() {
        try {
            return restClient
                    .post()
                    .uri(properties.url() + "/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CaptureResponse.class);
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CaptureResponse(List<ImageData> images) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ImageData(String name, String data) {}
    }
}
