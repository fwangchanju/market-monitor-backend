package dev.eolmae.marketmonitor.domain.stock.client;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.stock.dto.TokenRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.TokenResponse;
import dev.eolmae.marketmonitor.domain.stock.properties.KiwoomProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomTokenManager {

    private static final String TOKEN_URL = "https://api.kiwoom.com/oauth2/token";
    private static final int SUCCESS_CODE = 0;

    private final KiwoomProperties properties;
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        return refreshToken();
    }

    private String refreshToken() {
        log.info("Kiwoom 토큰 발급 요청");
        TokenResponse response = Optional.ofNullable(requestToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED));

        if (response.returnCode() != SUCCESS_CODE) {
            throw new BusinessException(ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED, response.returnMsg());
        }

        cachedToken = response.token();
        LocalDateTime expiresDt = LocalDateTime.parse(response.expiresAt(), DateTimePattern.DATETIME.formatter());
        tokenExpiry = expiresDt.minusMinutes(5).atZone(Zone.KST.zoneId()).toInstant();
        log.info("Kiwoom 토큰 발급 완료. 만료 예정: {}", tokenExpiry);
        return cachedToken;
    }

    private TokenResponse requestToken() {
        try {
            return restClient
                    .post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TokenRequest.of(properties.appKey(), properties.secret()))
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIWOOM_TOKEN_ISSUE_FAILED, e);
        }
    }
}
