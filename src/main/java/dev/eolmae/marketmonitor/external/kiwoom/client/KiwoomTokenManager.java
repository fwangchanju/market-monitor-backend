package dev.eolmae.marketmonitor.external.kiwoom.client;

import dev.eolmae.marketmonitor.exception.KiwoomApiException;
import dev.eolmae.marketmonitor.external.kiwoom.config.KiwoomProperties;
import dev.eolmae.marketmonitor.external.kiwoom.dto.TokenResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomTokenManager {

    private static final String TOKEN_URL = "https://api.kiwoom.com/oauth2/token";
    private static final int SUCCESS_CODE = 0;

    private final KiwoomProperties properties;
    private final RestClient restClient = RestClient.create();

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
        var body = Map.of(
                "grant_type", "client_credentials",
                "appkey", properties.appKey(),
                "secretkey", properties.secret());

        TokenResponse response = restClient
                .post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.returnCode() != SUCCESS_CODE) {
            String msg = response != null ? response.returnMsg() : "응답 없음";
            throw new KiwoomApiException("Kiwoom 토큰 발급 실패: " + msg);
        }

        cachedToken = response.token();
        LocalDateTime expiresDt =
                LocalDateTime.parse(response.expiresAt(), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        tokenExpiry = expiresDt.minusMinutes(5).atZone(ZoneId.of("Asia/Seoul")).toInstant();
        log.info("Kiwoom 토큰 발급 완료. 만료 예정: {}", tokenExpiry);
        return cachedToken;
    }
}
