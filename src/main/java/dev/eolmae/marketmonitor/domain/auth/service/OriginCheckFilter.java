package dev.eolmae.marketmonitor.domain.auth.service;

import dev.eolmae.marketmonitor.domain.auth.properties.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class OriginCheckFilter extends OncePerRequestFilter {

    private final AuthProperties authProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/") && isWriteMethod(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin == null || !allowedOrigins(request).contains(normalizeOrigin(origin))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "요청 출처를 확인할 수 없습니다.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isWriteMethod(String method) {
        return !HttpMethod.GET.matches(method)
                && !HttpMethod.HEAD.matches(method)
                && !HttpMethod.OPTIONS.matches(method);
    }

    private Set<String> allowedOrigins(HttpServletRequest request) {
        String requestOrigin = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if (!("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                && !("https".equalsIgnoreCase(request.getScheme()) && port == 443)) {
            requestOrigin += ":" + port;
        }
        Set<String> origins = new HashSet<>();
        origins.add(normalizeOrigin(requestOrigin));
        origins.add(normalizeOrigin(authProperties.getFrontendUrl()));
        return origins;
    }

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
                return "";
            }
            int port = uri.getPort();
            if (port == 80 && "http".equalsIgnoreCase(uri.getScheme())) {
                port = -1;
            }
            if (port == 443 && "https".equalsIgnoreCase(uri.getScheme())) {
                port = -1;
            }
            return new URI(
                            uri.getScheme().toLowerCase(Locale.ROOT),
                            null,
                            uri.getHost().toLowerCase(Locale.ROOT),
                            port,
                            null,
                            null,
                            null)
                    .toString();
        } catch (Exception e) {
            return "";
        }
    }
}
