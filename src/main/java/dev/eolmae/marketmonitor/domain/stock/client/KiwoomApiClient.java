package dev.eolmae.marketmonitor.domain.stock.client;

import com.google.common.util.concurrent.RateLimiter;
import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.stock.dto.KiwoomRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.KiwoomResponse;
import dev.eolmae.marketmonitor.domain.stock.exception.KiwoomRateLimitException;
import dev.eolmae.marketmonitor.domain.stock.properties.KiwoomProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomApiClient {

    private static final String BASE_URL = "https://api.kiwoom.com";
    public static final String SUCCESS_CODE = "0";

    private final KiwoomProperties properties;
    private final KiwoomTokenManager tokenManager;
    private final RestClient restClient;

    @SuppressWarnings("UnstableApiUsage")
    private final RateLimiter rateLimiter;

    /**
     * 타입 안전 API 호출. request DTO가 직렬화되어 요청 바디로 전송되고, 응답은 dataClass 타입으로 역직렬화된다.
     * 호출마다 callIntervalMs 딜레이를 적용한다.
     *
     * 429 응답 시 최대 3회 재시도(2초 간격), 초과 시 해당 사이클 스킵.
     */
    @SuppressWarnings("UnstableApiUsage")
    @Retryable(retryFor = KiwoomRateLimitException.class, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public <T extends KiwoomResponse> T post(KiwoomRequest request, Class<T> dataClass) {
        log.debug("Kiwoom API 호출: apiId={}, path={}", request.apiId(), request.path());
        rateLimiter.acquire();
        return fetchResponse(request, dataClass);
    }

    @Recover
    public <T> T recoverFromRateLimit(KiwoomRateLimitException e, KiwoomRequest request, Class<T> dataClass) {
        log.warn("Kiwoom API rate limit 재시도 횟수 초과, 사이클 스킵: apiId={}", request.apiId());
        throw new BusinessException(ErrorCode.KIWOOM_RATE_LIMIT, request.apiId());
    }

    private <T extends KiwoomResponse> T fetchResponse(KiwoomRequest request, Class<T> dataClass) {
        T response = Optional.ofNullable(requestApi(request, dataClass))
                .orElseThrow(() -> new BusinessException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, request.apiId()));

        if (!SUCCESS_CODE.equals(response.returnCode())) {
            log.warn(
                    "Kiwoom API 오류 응답: apiId={}, return_code={}, msg={}",
                    request.apiId(),
                    response.returnCode(),
                    response.returnMsg());
            throw new BusinessException(ErrorCode.KIWOOM_ERROR_RESPONSE, request.apiId(), response.returnMsg());
        }

        return response;
    }

    private <T> T requestApi(KiwoomRequest request, Class<T> dataClass) {
        try {
            return restClient
                    .post()
                    .uri(BASE_URL + request.path())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("authorization", "Bearer " + tokenManager.getToken())
                    .header("appkey", properties.appKey())
                    .header("secretkey", properties.secret())
                    .header("api-id", request.apiId())
                    .body(request)
                    .retrieve()
                    .body(dataClass);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Kiwoom API 429 rate limit: apiId={}", request.apiId());
                throw new KiwoomRateLimitException(ErrorCode.KIWOOM_RATE_LIMIT, request.apiId());
            }
            throw new BusinessException(ErrorCode.KIWOOM_HTTP_ERROR, e, request.apiId());
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, e, request.apiId());
        }
    }
}
