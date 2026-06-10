package dev.eolmae.marketmonitor.domain.stock.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import dev.eolmae.marketmonitor.domain.stock.exception.KiwoomApiException;
import dev.eolmae.marketmonitor.domain.stock.exception.KiwoomRateLimitException;
import dev.eolmae.marketmonitor.domain.stock.properties.KiwoomProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomApiClient {

    private static final String BASE_URL = "https://api.kiwoom.com";
    public static final String SUCCESS_CODE = "0";

    private final KiwoomProperties properties;
    private final KiwoomTokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper(); // ObjectMapper 가 꼭 필요한가? 어차피 직렬화 역직렬화 알아서 해주는거 아닌가?
    private final RestClient restClient;

    @SuppressWarnings("UnstableApiUsage")
    private final RateLimiter rateLimiter;

    /**
     * 타입 안전 API 호출. request DTO가 직렬화되어 요청 바디로 전송된다.
     * 응답 전체(rt_cd, msg1 포함 root object)를 dataClass 타입으로 역직렬화해 반환한다.
     * 호출마다 callIntervalMs 딜레이를 적용한다.
     *
     * 429 응답 시 최대 3회 재시도(2초 간격), 초과 시 해당 사이클 스킵.
     */
    @SuppressWarnings("UnstableApiUsage")
    @Retryable(retryFor = KiwoomRateLimitException.class, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public <T> T post(BaseRequest request, Class<T> dataClass) {
        log.debug("Kiwoom API 호출: apiId={}, path={}", request.apiId(), request.path());

        rateLimiter.acquire();

        JsonNode response = callApi(request.path(), request.apiId(), request);
        return objectMapper.convertValue(response, dataClass);
    }

    @Recover
    public <T> T recoverFromRateLimit(KiwoomRateLimitException e, BaseRequest request, Class<T> dataClass) {
        log.warn("Kiwoom API rate limit 재시도 횟수 초과, 사이클 스킵: apiId={}", request.apiId());
        throw new KiwoomApiException("Kiwoom API rate limit 초과로 사이클 스킵: " + request.apiId());
    }

    private JsonNode callApi(String path, String apiId, Object requestBody) {
        String raw;
        try {
            raw = restClient
                    .post()
                    .uri(BASE_URL + path)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("authorization", "Bearer " + tokenManager.getToken())
                    .header("appkey", properties.appKey())
                    .header("secretkey", properties.secret())
                    .header("api-id", apiId)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Kiwoom API 429 rate limit: apiId={}", apiId);
                throw new KiwoomRateLimitException("Kiwoom API 429: apiId=" + apiId);
            }
            throw new KiwoomApiException("Kiwoom API HTTP 오류: " + e.getMessage(), e);
        }

        JsonNode response;
        try {
            response = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new KiwoomApiException("Kiwoom API 응답 파싱 실패: " + raw, e);
        }

        String returnCode = response.path("return_code").asText();
        if (!SUCCESS_CODE.equals(returnCode)) {
            log.warn(
                    "Kiwoom API 오류 응답: apiId={}, return_code={}, msg={}",
                    apiId,
                    returnCode,
                    response.path("return_msg").asText());
            throw new KiwoomApiException(
                    "Kiwoom API 오류: " + response.path("return_msg").asText());
        }

        return response;
    }
}
