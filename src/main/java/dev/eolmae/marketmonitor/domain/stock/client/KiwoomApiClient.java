package dev.eolmae.marketmonitor.domain.stock.client;

import com.google.common.util.concurrent.RateLimiter;
import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.stock.dto.KiwoomRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.KiwoomResponse;
import dev.eolmae.marketmonitor.domain.stock.dto.KiwoomResponseHeader;
import dev.eolmae.marketmonitor.domain.stock.exception.KiwoomRateLimitException;
import dev.eolmae.marketmonitor.domain.stock.properties.KiwoomProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
     * 응답 헤더의 cont-yn이 Y인 동안 next-key로 계속 이어서 호출하고, 페이지들을 병합해서 하나로 리턴한다.
     *
     * 429 응답 시 최대 3회 재시도(2초 간격), 초과 시 해당 사이클 스킵.
     * 재시도는 이 메서드 전체 단위로 걸리므로, 페이지네이션 도중 429가 나면 첫 페이지부터 다시 돈다.
     */
    @Retryable(retryFor = KiwoomRateLimitException.class, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public <T extends KiwoomResponse> T post(KiwoomRequest request, Class<T> dataClass) {
        log.debug("Kiwoom API 호출: apiId={}, path={}", request.apiId(), request.path());
        return fetchAll(request, dataClass);
    }

    @Recover
    public <T> T recoverFromRateLimit(KiwoomRateLimitException e, KiwoomRequest request, Class<T> dataClass) {
        log.warn("Kiwoom API rate limit 재시도 횟수 초과, 사이클 스킵: apiId={}", request.apiId());
        throw new BusinessException(ErrorCode.KIWOOM_RATE_LIMIT, request.apiId());
    }

    @SuppressWarnings("unchecked")
    private <T extends KiwoomResponse> T fetchAll(KiwoomRequest request, Class<T> dataClass) {
        PageResult<T> page = fetchPage(request, dataClass);
        T merged = page.body();

        while (page.header().hasNext()) {
            page = fetchPage(request, dataClass, page.header().nextKey());
            merged = (T) merged.mergeNext(page.body());
        }

        return merged;
    }

    private <T extends KiwoomResponse> PageResult<T> fetchPage(KiwoomRequest request, Class<T> dataClass) {
        return fetchPage(request, dataClass, null);
    }

    @SuppressWarnings("UnstableApiUsage")
    private <T extends KiwoomResponse> PageResult<T> fetchPage(KiwoomRequest request, Class<T> dataClass, String nextKey) {
        rateLimiter.acquire();
        ResponseEntity<T> entity = fetch(request, dataClass, nextKey);
        return toPageResult(request, entity);
    }

    private <T> ResponseEntity<T> fetch(KiwoomRequest request, Class<T> dataClass, String nextKey) {
        try {
            return restClient
                    .post()
                    .uri(BASE_URL + request.path())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> {
                        httpHeaders.set("authorization", "Bearer " + tokenManager.getToken());
                        httpHeaders.set("appkey", properties.appKey());
                        httpHeaders.set("secretkey", properties.secret());
                        httpHeaders.set("api-id", request.apiId());

                        if (nextKey != null) {
                            httpHeaders.set("cont-yn", "Y");
                            httpHeaders.set("next-key", nextKey);
                        }
                    })
                    .body(request)
                    .retrieve()
                    .toEntity(dataClass);
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

    private <T extends KiwoomResponse> PageResult<T> toPageResult(KiwoomRequest request, ResponseEntity<T> entity) {
        T validated = Optional.ofNullable(entity.getBody())
                .orElseThrow(() -> new BusinessException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, request.apiId()));

        if (!SUCCESS_CODE.equals(validated.returnCode())) {
            log.warn(
                    "Kiwoom API 오류 응답: apiId={}, return_code={}, msg={}",
                    request.apiId(),
                    validated.returnCode(),
                    validated.returnMsg());
            throw new BusinessException(ErrorCode.KIWOOM_ERROR_RESPONSE, request.apiId(), validated.returnMsg());
        }

        return new PageResult<>(KiwoomResponseHeader.from(entity.getHeaders()), validated);
    }

    private record PageResult<T extends KiwoomResponse>(KiwoomResponseHeader header, T body) {}
}
