package dev.eolmae.marketmonitor.domain.krx.crawler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.krx.client.KrxAuthClient;
import dev.eolmae.marketmonitor.domain.krx.dto.KrxDataMarketRequest;
import dev.eolmae.marketmonitor.domain.krx.dto.KrxDataMarketResponse;
import dev.eolmae.marketmonitor.domain.krx.enums.KrxResponseCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "krx.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KrxCrawler {

    private static final String KRX_DATA_URL = "https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd";
    private static final String KRX_REFERER = "https://data.krx.co.kr/contents/MDC/MDI/outerLoader/index.cmd";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0";
    /** KRX 데이터마켓 응답 바디에서 실제 데이터 배열이 담기는 키. */
    private static final String RESPONSE_DATA_KEY = "OutBlock_1";

    private final KrxAuthClient krxAuthClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public <T extends KrxDataMarketResponse> List<T> fetch(KrxDataMarketRequest request, Class<T> dataClass) {
        String cookie = krxAuthClient.getCookie();
        String responseBody = doFetch(request, cookie);

        if (KrxResponseCode.SESSION_EXPIRED.matches(responseBody)) {
            log.warn("KRX LOGOUT 응답 — 재로그인 후 재시도: bld={}", request.bld());
            cookie = krxAuthClient.refreshCookie();
            responseBody = doFetch(request, cookie);

            if (KrxResponseCode.SESSION_EXPIRED.matches(responseBody)) {
                throw new EscalateException(ErrorCode.KRX_SESSION_EXPIRED, request.bld());
            }
        }

        return parseItems(responseBody, request.bld(), dataClass);
    }

    private String doFetch(KrxDataMarketRequest request, String cookie) {
        return restClient
                .post()
                .uri(KRX_DATA_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("User-Agent", USER_AGENT)
                .header("Referer", KRX_REFERER)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cookie", cookie)
                .body(request.toMultiValueMap())
                .retrieve()
                .body(String.class);
    }

    private <T extends KrxDataMarketResponse> List<T> parseItems(String responseBody, String bld, Class<T> dataClass) {
        JavaType envelopeType =
                objectMapper.getTypeFactory().constructParametricType(KrxBlockEnvelope.class, dataClass);

        KrxBlockEnvelope<T> envelope;
        try {
            envelope = objectMapper.readValue(responseBody, envelopeType);
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.KRX_RESPONSE_INVALID, e, bld);
        }

        if (envelope.items() == null) {
            throw new EscalateException(ErrorCode.KRX_RESPONSE_INVALID, bld);
        }

        log.debug("KRX 응답 수신: bld={}, 항목수={}", bld, envelope.items().size());
        return envelope.items();
    }

    private record KrxBlockEnvelope<T>(
            @JsonProperty(RESPONSE_DATA_KEY) List<T> items) {}
}
