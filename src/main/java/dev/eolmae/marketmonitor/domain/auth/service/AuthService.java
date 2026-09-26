package dev.eolmae.marketmonitor.domain.auth.service;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.event.UserSignedUpEvent;
import dev.eolmae.marketmonitor.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketmonitor.domain.auth.entity.UserAccount;
import dev.eolmae.marketmonitor.domain.auth.entity.UserRefreshToken;
import dev.eolmae.marketmonitor.domain.auth.properties.AuthProperties;
import dev.eolmae.marketmonitor.domain.auth.repository.UserAccountRepository;
import dev.eolmae.marketmonitor.domain.auth.repository.UserRefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final int REFRESH_TOKEN_DAYS = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final AuthProperties authProperties;
    private final UserAccountRepository userAccountRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AppJwtService appJwtService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public IssuedTokens loginWithGoogle(String code, String codeVerifier, String redirectUri, long requestStartedAt) {
        requireGoogleConfiguration();
        String idToken = exchangeCodeForIdToken(code, codeVerifier, redirectUri);
        Jwt googleUser = verifyGoogleIdToken(idToken);

        String issuer = googleUser.getIssuer().toString();
        String subject = googleUser.getSubject();
        String email = googleUser.getClaimAsString("email");
        if (subject == null || email == null || !Boolean.TRUE.equals(googleUser.getClaim("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "확인된 Google 계정 정보가 필요합니다.");
        }

        UserAccount user =
                userAccountRepository.findByIssuerAndSub(issuer, subject).orElse(null);
        if (user == null) {
            // ON CONFLICT DO NOTHING + RETURNING — 동시 로그인 레이스에서 실제로 삽입된 행만 받아
            // 신규 가입 여부를 판정한다. JPQL/QueryDSL에 없는 문법이라 JdbcTemplate으로 직접 쓴다.
            List<Long> insertedIds =
                    jdbcTemplate.query("""
                    INSERT INTO users (issuer, sub, email, role, created_at, updated_at)
                    VALUES (?, ?, ?, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (issuer, sub) DO NOTHING
                    RETURNING id
                    """, (resultSet, rowNumber) -> resultSet.getLong(1), issuer, subject, email);
            if (!insertedIds.isEmpty()) {
                long userInsertedAt = System.nanoTime();
                Long newUserId = insertedIds.getFirst();
                log.info(
                        "신규 가입 users INSERT 완료: userId={}, elapsedMs={}",
                        newUserId,
                        TimeUnit.NANOSECONDS.toMillis(userInsertedAt - requestStartedAt));
                eventPublisher.publishEvent(new UserSignedUpEvent(newUserId));
                long initializedAt = System.nanoTime();
                log.info(
                        "신규 가입 템플릿 복제 완료: userId={}, elapsedMs={}",
                        newUserId,
                        TimeUnit.NANOSECONDS.toMillis(initializedAt - userInsertedAt));
                user = userAccountRepository.findById(newUserId).orElseThrow();
            } else {
                user = userAccountRepository.findByIssuerAndSub(issuer, subject).orElseThrow();
                user.updateEmail(email);
            }
        } else {
            user.updateEmail(email);
        }

        return createTokens(user);
    }

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션을 갱신할 수 없습니다.");
        }
        UserRefreshToken token = userRefreshTokenRepository
                .findByTokenHash(hash(rawRefreshToken))
                .filter(refreshToken -> refreshToken.isUsableAt(LocalDateTime.now(Zone.KST.zoneId())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션을 갱신할 수 없습니다."));
        token.revoke();
        return createTokens(token.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        userRefreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(UserRefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public AuthSessionResponse session(AuthenticatedUserPrincipal principal) {
        if (principal == null) {
            return AuthSessionResponse.anonymous();
        }
        return userAccountRepository
                .findById(principal.userId())
                .map(user -> new AuthSessionResponse(true, user.getId(), user.getEmail(), user.getRole()))
                .orElseGet(AuthSessionResponse::anonymous);
    }

    private IssuedTokens createTokens(UserAccount user) {
        var principal = new AuthenticatedUserPrincipal(user.getId(), user.getRole());
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        userRefreshTokenRepository.save(UserRefreshToken.create(
                user, hash(refreshToken), LocalDateTime.now(Zone.KST.zoneId()).plusDays(REFRESH_TOKEN_DAYS)));
        return new IssuedTokens(appJwtService.issueAccessToken(principal), refreshToken);
    }

    private String exchangeCodeForIdToken(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", authProperties.getGoogle().getClientId());
        body.add("client_secret", authProperties.getGoogle().getClientSecret());
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");
        body.add("code_verifier", codeVerifier);
        try {
            var response = restClient
                    .post()
                    .uri(GOOGLE_TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<GoogleTokenResponse>() {});
            if (response == null || response.idToken() == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 인증 응답을 확인할 수 없습니다.");
            }
            return response.idToken();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다.");
        }
    }

    private Jwt verifyGoogleIdToken(String idToken) {
        try {
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER));
            Jwt jwt = decoder.decode(idToken);
            OAuth2TokenValidatorResult audienceValidation = jwt.getAudience()
                            .contains(authProperties.getGoogle().getClientId())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token", "Google token audience mismatch", null));
            if (audienceValidation.hasErrors()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 인증 토큰이 유효하지 않습니다.");
            }
            return jwt;
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 인증 토큰이 유효하지 않습니다.");
        }
    }

    private void requireGoogleConfiguration() {
        if (authProperties.getGoogle().getClientId().isBlank()
                || authProperties.getGoogle().getClientSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google 로그인이 설정되지 않았습니다.");
        }
        if (authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "JWT 서명 키가 설정되지 않았습니다.");
        }
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("토큰 처리에 실패했습니다.", e);
        }
    }

    private record GoogleTokenResponse(String id_token) {

        String idToken() {
            return id_token;
        }
    }

    public record IssuedTokens(String accessToken, String refreshToken) {}
}
