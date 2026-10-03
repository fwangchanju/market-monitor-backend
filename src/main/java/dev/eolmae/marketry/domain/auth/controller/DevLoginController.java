package dev.eolmae.marketry.domain.auth.controller;

import dev.eolmae.marketry.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.auth.service.AuthService;
import dev.eolmae.marketry.domain.auth.service.AuthService.IssuedTokens;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 프론트엔드 개발 전용 로그인. IP 화이트리스트 인증 브릿지(AuthTokenFilter의 999999 매핑, PR #132에서
 * 제거)를 대체한다. marketry.owner-user-id로 로그인하며, 구글 로그인과 동일한 쿠키·응답 형태를
 * 사용한다.
 *
 * <p>운영 환경 차단은 두 겹이다. {@code @Profile("!prod")}로 prod 프로필에서는 빈 자체가 생성되지 않고,
 * {@code auth.dev-login.enabled}가 true일 때만 추가로 켜진다. 기본 application.properties는 이 값을
 * true로 고정하고(사실상 로컬 전용 설정 파일), application-prod.properties가 false로 덮어쓴다.
 */
@RestController
@RequestMapping("/api/auth")
@Profile("!prod")
@ConditionalOnProperty(name = "auth.dev-login.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DevLoginController {

    private final AuthService authService;
    private final AppJwtService appJwtService;
    private final AuthCookies authCookies;
    private final MarketryProperties marketryProperties;

    @PostConstruct
    void warnDevLoginActive() {
        log.warn("dev-login enabled — local development only");
    }

    @PostMapping("/dev-login")
    public AuthSessionResponse devLogin(HttpServletResponse response) {
        IssuedTokens tokens = authService.loginAsForDevelopment(marketryProperties.ownerUserId());
        authCookies.setSessionCookies(response, tokens);
        return authService.session(appJwtService.parse(tokens.accessToken()));
    }
}
