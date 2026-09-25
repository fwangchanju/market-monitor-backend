package dev.eolmae.marketmonitor.domain.auth.service;

import dev.eolmae.marketmonitor.domain.access.enums.Role;
import dev.eolmae.marketmonitor.domain.access.service.AllowedIpAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String ACCESS_COOKIE = "mm_access";
    private static final String LEGACY_OWNER_IP_HEADER = "X-Real-IP";
    private static final long LEGACY_OWNER_ID = 999999L;
    private static final String CAPTURE_TOKEN_HEADER = "X-Capture-Token";

    private final AppJwtService appJwtService;
    private final AllowedIpAccessService allowedIpAccessService;
    private final LegacyCompatibilityBackfillState backfillState;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isApiRequest(request) && !backfillState.isComplete()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service startup is in progress.");
            return;
        }
        String captureToken = request.getHeader(CAPTURE_TOKEN_HEADER);
        if (captureToken != null) {
            authenticateCaptureToken(captureToken, request, response, filterChain);
            return;
        }
        String token = bearerToken(request);
        if (token == null && request.getCookies() != null) {
            token = Arrays.stream(request.getCookies())
                    .filter(cookie -> ACCESS_COOKIE.equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (token != null) {
            AuthenticatedUserPrincipal principal = appJwtService.parse(token);
            if (principal != null) {
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            }
        }
        if (token == null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && isLegacyAdminRoute(request)) {
            String realIp = request.getHeader(LEGACY_OWNER_IP_HEADER);
            // Remove after the new frontend and owner-data migration are live, before the nginx IP gate is removed.
            // Production safety depends on nginx overwriting X-Real-IP and the backend port staying loopback-only.
            if (realIp != null && allowedIpAccessService.isAllowedAdmin(realIp)) {
                AuthenticatedUserPrincipal legacyOwner = new AuthenticatedUserPrincipal(LEGACY_OWNER_ID, Role.ADMIN);
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(
                                legacyOwner, null, legacyOwner.getAuthorities()));
            }
        }
        filterChain.doFilter(request, response);
    }

    // 캡처 토큰은 렌더러가 /capture 본문으로만 전달하는 소유자 읽기 전용 인증이다. 쿠키에서는 절대
    // 읽지 않고, 전용 헤더로만 받는다. GET 조회만 허용하고 관리자 API·토큰 재발급·로그아웃은 거부한다.
    private void authenticateCaptureToken(
            String captureToken, HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuthenticatedUserPrincipal principal = appJwtService.parseCaptureToken(captureToken);
        if (principal == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired capture token.");
            return;
        }
        if (!isCaptureTokenAllowedRoute(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Capture token is not allowed for this request.");
            return;
        }
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        filterChain.doFilter(request, response);
    }

    private boolean isCaptureTokenAllowedRoute(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getServletPath();
        boolean tokenLifecycleRoute = "/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path);
        return !isAdminRoute(path) && !tokenLifecycleRoute;
    }

    private boolean isLegacyAdminRoute(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean customMapRoute = "/api/map".equals(path) && "true".equals(request.getParameter("isCustom"));
        boolean customMapMutation = "/api/map/reset".equals(path)
                || "/api/map/excluded-categories".equals(path)
                || path.startsWith("/api/map/excluded-categories/");
        return isAdminRoute(path) || customMapRoute || customMapMutation;
    }

    private boolean isAdminRoute(String path) {
        return "/api/admin".equals(path)
                || path.startsWith("/api/admin/")
                || "/api/watch-stocks".equals(path)
                || path.startsWith("/api/watch-stocks/");
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/api".equals(path) || path.startsWith("/api/");
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length());
    }
}
