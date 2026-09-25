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

    private boolean isLegacyAdminRoute(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean adminRoute = "/api/admin".equals(path)
                || path.startsWith("/api/admin/")
                || "/api/watch-stocks".equals(path)
                || path.startsWith("/api/watch-stocks/");
        boolean customMapRoute = "/api/map".equals(path) && "true".equals(request.getParameter("isCustom"));
        boolean customMapMutation = "/api/map/reset".equals(path)
                || "/api/map/excluded-categories".equals(path)
                || path.startsWith("/api/map/excluded-categories/");
        return adminRoute || customMapRoute || customMapMutation;
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
