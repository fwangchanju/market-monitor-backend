package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.dto.TossMarketCalendarResponse;
import dev.eolmae.marketry.domain.stock.dto.TossTokenResponse;
import dev.eolmae.marketry.domain.stock.properties.TossProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TossMarketCalendarClient {
    private static final String BASE_URL = "https://openapi.tossinvest.com";
    private static final long EXPIRY_MARGIN_SECONDS = 30;
    private final TossProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private String token;
    private Instant expiresAt = Instant.MIN;

    @Autowired
    public TossMarketCalendarClient(TossProperties properties, RestClient restClient) {
        this(properties, restClient, Clock.systemUTC());
    }

    TossMarketCalendarClient(TossProperties properties, RestClient restClient, Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;
    }

    public TossMarketCalendarResponse fetch(LocalDate date) {
        String accessToken = acquireToken();
        try {
            TossMarketCalendarResponse response = restClient
                    .get()
                    .uri(BASE_URL + "/api/v1/market-calendar/KR?date={date}", date)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(TossMarketCalendarResponse.class);
            if (response == null) {
                throw new BadRequestException(ErrorCode.MARKET_CALENDAR_RESPONSE_INVALID);
            }
            return response;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                invalidateToken(accessToken);
            }
            // 인증 오류 본문과 원인 예외에는 자격 증명이 포함될 수 있으므로 전달하지 않는다.
            throw new BadRequestException(
                    ErrorCode.TOSS_CALENDAR_FETCH_FAILED, e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new BadRequestException(ErrorCode.TOSS_CALENDAR_FETCH_FAILED, classifyConnectionFailure(e));
        } catch (BadRequestException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BadRequestException(ErrorCode.TOSS_CALENDAR_FETCH_FAILED, "RESPONSE_PARSE");
        } catch (RuntimeException e) {
            throw new BadRequestException(
                    ErrorCode.TOSS_CALENDAR_FETCH_FAILED, e.getClass().getSimpleName());
        }
    }

    private synchronized String acquireToken() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(expiresAt)) {
            return token;
        }
        if (properties.clientId() == null
                || properties.clientId().isBlank()
                || properties.clientSecret() == null
                || properties.clientSecret().isBlank()) {
            throw new BadRequestException(ErrorCode.TOSS_AUTH_NOT_CONFIGURED);
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        try {
            TossTokenResponse response = restClient
                    .post()
                    .uri(BASE_URL + "/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TossTokenResponse.class);
            if (response == null
                    || response.accessToken() == null
                    || response.accessToken().isBlank()
                    || response.expiresIn() == null
                    || response.expiresIn() <= 0) {
                throw new BadRequestException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED, "RESPONSE_INVALID");
            }
            token = response.accessToken();
            expiresAt = now.plusSeconds(Math.max(0, response.expiresIn() - EXPIRY_MARGIN_SECONDS));
            return token;
        } catch (RestClientResponseException e) {
            throw new BadRequestException(
                    ErrorCode.TOSS_TOKEN_ISSUE_FAILED, e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new BadRequestException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED, classifyConnectionFailure(e));
        } catch (BadRequestException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BadRequestException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED, "RESPONSE_PARSE");
        } catch (RuntimeException e) {
            throw new BadRequestException(
                    ErrorCode.TOSS_TOKEN_ISSUE_FAILED, e.getClass().getSimpleName());
        }
    }

    private String classifyConnectionFailure(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof java.net.http.HttpTimeoutException
                    || cause instanceof java.net.SocketTimeoutException) {
                return "TIMEOUT";
            }
            cause = cause.getCause();
        }
        return "CONNECTION_FAILED";
    }

    private synchronized void invalidateToken(String failedToken) {
        if (failedToken.equals(token)) {
            token = null;
            expiresAt = Instant.MIN;
        }
    }
}
