package dev.eolmae.marketmonitor.domain.krx.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.krx.client.KrxAuthClient;
import dev.eolmae.marketmonitor.domain.krx.enums.KrxResponseCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "krx.collection.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KrxCrawler {

    private static final String KRX_DATA_URL = "https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd";
    private static final String KRX_REFERER = "https://data.krx.co.kr/contents/MDC/MDI/outerLoader/index.cmd";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0";

    private final KrxAuthClient krxAuthClient;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<JsonNode> fetch(Map<String, String> params) {
        String cookie = krxAuthClient.getCookie();
        String responseBody = doFetch(params, cookie);

        if (KrxResponseCode.SESSION_EXPIRED.matches(responseBody)) {
            log.warn("KRX LOGOUT 응답 — 재로그인 후 재시도: bld={}", params.get("bld"));
            cookie = krxAuthClient.refreshCookie();
            responseBody = doFetch(params, cookie);

            if (KrxResponseCode.SESSION_EXPIRED.matches(responseBody)) {
                throw new EscalateException(ErrorCode.KRX_SESSION_EXPIRED, params.get("bld"));
            }
        }

        return parseOutBlock(responseBody, params.get("bld"));
    }

    private String doFetch(Map<String, String> params, String cookie) {
        MultiValueMap<String, String> formData = toMultiValueMap(params);
        return restClient
                .post()
                .uri(KRX_DATA_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("User-Agent", USER_AGENT)
                .header("Referer", KRX_REFERER)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cookie", cookie)
                .body(formData)
                .retrieve()
                .body(String.class);
    }

    private List<JsonNode> parseOutBlock(String responseBody, String bld) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.KRX_RESPONSE_INVALID, e, bld);
        }

        JsonNode outBlock = root.get("OutBlock_1");
        if (outBlock == null || outBlock.isNull()) {
            throw new EscalateException(ErrorCode.KRX_RESPONSE_INVALID, bld);
        }

        List<JsonNode> result = new ArrayList<>();
        outBlock.forEach(result::add);
        log.debug("KRX 응답 수신: bld={}, 항목수={}", bld, result.size());
        return result;
    }

    private MultiValueMap<String, String> toMultiValueMap(Map<String, String> params) {
        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        params.forEach(multiValueMap::add);
        return multiValueMap;
    }
}
