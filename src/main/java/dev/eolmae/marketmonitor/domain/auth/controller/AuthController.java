package dev.eolmae.marketmonitor.domain.auth.controller;

import dev.eolmae.marketmonitor.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketmonitor.domain.auth.properties.AuthProperties;
import dev.eolmae.marketmonitor.domain.auth.service.AppJwtService;
import dev.eolmae.marketmonitor.domain.auth.service.AuthService;
import dev.eolmae.marketmonitor.domain.auth.service.AuthService.IssuedTokens;
import dev.eolmae.marketmonitor.domain.auth.service.AuthenticatedUserPrincipal;
import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String STATE_COOKIE = "mm_oauth_state";
    private static final String VERIFIER_COOKIE = "mm_oauth_verifier";
    private static final String RETURN_TO_COOKIE = "mm_oauth_return";
    private static final String ACCESS_COOKIE = "mm_access";
    private static final String REFRESH_COOKIE = "mm_refresh";
    private static final String CALLBACK_PATH = "/api/auth/google/callback";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final AppJwtService appJwtService;

    @GetMapping("/google")
    public RedirectView beginGoogleLogin(
            @RequestParam(required = false) String returnTo, HttpServletResponse response) {
        if (authProperties.getGoogle().getClientId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google 로그인이 설정되지 않았습니다.");
        }
        String state = randomUrlToken(32);
        String verifier = randomUrlToken(48);
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        String redirectUri = callbackUri();
        String authorizationUrl = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", authProperties.getGoogle().getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
        addCookie(response, STATE_COOKIE, state, "/api/auth/google", Duration.ofMinutes(10));
        addCookie(response, VERIFIER_COOKIE, verifier, "/api/auth/google", Duration.ofMinutes(10));
        addCookie(
                response,
                RETURN_TO_COOKIE,
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(safeReturnTo(returnTo).getBytes(StandardCharsets.UTF_8)),
                "/api/auth/google",
                Duration.ofMinutes(10));
        return new RedirectView(authorizationUrl);
    }

    @GetMapping("/google/callback")
    public RedirectView completeGoogleLogin(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        long requestStartedAt = System.nanoTime();
        String expectedState = cookie(request, STATE_COOKIE);
        String verifier = cookie(request, VERIFIER_COOKIE);
        if (expectedState == null
                || verifier == null
                || verifier.isBlank()
                || !MessageDigest.isEqual(
                        expectedState.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OAuth state 검증에 실패했습니다.");
        }

        expireCookie(response, STATE_COOKIE, "/api/auth/google");
        expireCookie(response, VERIFIER_COOKIE, "/api/auth/google");
        String returnTo = decodedReturnTo(cookie(request, RETURN_TO_COOKIE));
        expireCookie(response, RETURN_TO_COOKIE, "/api/auth/google");
        IssuedTokens tokens = authService.loginWithGoogle(code, verifier, callbackUri(), requestStartedAt);
        setTokens(response, tokens);
        return new RedirectView(authProperties.getFrontendUrl() + returnTo);
    }

    @GetMapping("/session")
    @ResponseBody
    public AuthSessionResponse session() {
        AuthenticatedUserPrincipal principal = CurrentUser.current();
        return authService.session(principal);
    }

    @PostMapping("/refresh")
    @ResponseBody
    public AuthSessionResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        IssuedTokens tokens = authService.refresh(cookie(request, REFRESH_COOKIE));
        setTokens(response, tokens);
        return authService.session(appJwtService.parse(tokens.accessToken()));
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookie(request, REFRESH_COOKIE));
        expireCookie(response, ACCESS_COOKIE, "/");
        expireCookie(response, REFRESH_COOKIE, "/api/auth");
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    private String callbackUri() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(CALLBACK_PATH)
                .toUriString();
    }

    private void setTokens(HttpServletResponse response, IssuedTokens tokens) {
        addCookie(response, ACCESS_COOKIE, tokens.accessToken(), "/", Duration.ofMinutes(15));
        addCookie(response, REFRESH_COOKIE, tokens.refreshToken(), "/api/auth", Duration.ofDays(14));
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, value)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Lax")
                        .path(path)
                        .maxAge(maxAge)
                        .build()
                        .toString());
    }

    private void expireCookie(HttpServletResponse response, String name, String path) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, "")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Lax")
                        .path(path)
                        .maxAge(Duration.ZERO)
                        .build()
                        .toString());
    }

    private String safeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return "/";
        }
        if (!returnTo.startsWith("/")
                || returnTo.startsWith("//")
                || returnTo.contains("\\")
                || returnTo.contains("://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 로그인 복귀 경로입니다.");
        }
        return returnTo;
    }

    private String decodedReturnTo(String encoded) {
        if (encoded == null) {
            return "/";
        }
        try {
            return safeReturnTo(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }

    private String randomUrlToken(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("OAuth code verifier를 처리할 수 없습니다.", e);
        }
    }

    private String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
