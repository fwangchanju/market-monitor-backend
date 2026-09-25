package dev.eolmae.marketmonitor.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.domain.access.enums.Role;
import dev.eolmae.marketmonitor.domain.access.service.AllowedIpAccessService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthTokenFilterTest {

    private static final String ADMIN_IP = "203.0.113.9";

    private final AppJwtService appJwtService = mock(AppJwtService.class);
    private final AllowedIpAccessService allowedIpAccessService = mock(AllowedIpAccessService.class);
    private final LegacyCompatibilityBackfillState backfillState = new LegacyCompatibilityBackfillState();
    private final AuthTokenFilter filter = new AuthTokenFilter(appJwtService, allowedIpAccessService, backfillState);

    @BeforeEach
    void clearSecurityContextBeforeEach() {
        SecurityContextHolder.clearContext();
        backfillState.markComplete();
    }

    @AfterEach
    void clearSecurityContextAfterEach() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/admin/market-map/categories",
                "/api/watch-stocks",
                "/api/map/excluded-categories/7",
                "/api/map/reset"
            })
    void adminIpTemporarilyAuthenticatesLegacyRoutesAsTheMigratedOwner(String path) throws Exception {
        when(allowedIpAccessService.isAllowedAdmin(ADMIN_IP)).thenReturn(true);

        filter.doFilter(request(path), new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUserPrincipal(999999L, Role.ADMIN));
        verify(allowedIpAccessService).isAllowedAdmin(ADMIN_IP);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/admin/market-map/categories",
                "/api/watch-stocks",
                "/api/map/excluded-categories/7",
                "/api/map/reset"
            })
    void nonAdminIpDoesNotAuthenticateLegacyRoutes(String path) throws Exception {
        when(allowedIpAccessService.isAllowedAdmin(ADMIN_IP)).thenReturn(false);

        filter.doFilter(request(path), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(allowedIpAccessService).isAllowedAdmin(ADMIN_IP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/custom/sectors", "/api/summary", "/api/watch-stocks-archive"})
    void compatibilityAuthenticationDoesNotApplyOutsideLegacyAdminRoutes(String path) throws Exception {
        filter.doFilter(request(path), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(allowedIpAccessService);
    }

    @Test
    void legacyCustomMapModeRequiresAnAdminIpButDefaultMapDoesNotUseTheBridge() throws Exception {
        when(allowedIpAccessService.isAllowedAdmin(ADMIN_IP)).thenReturn(true);
        MockHttpServletRequest customMapRequest = request("/api/map");
        customMapRequest.setParameter("isCustom", "true");

        filter.doFilter(customMapRequest, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new AuthenticatedUserPrincipal(999999L, Role.ADMIN));
        verify(allowedIpAccessService).isAllowedAdmin(ADMIN_IP);

        SecurityContextHolder.clearContext();
        MockHttpServletRequest defaultMapRequest = request("/api/map");
        defaultMapRequest.setParameter("isCustom", "false");
        filter.doFilter(defaultMapRequest, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidJwtDoesNotFallBackToLegacyIpAuthentication() throws Exception {
        when(allowedIpAccessService.isAllowedAdmin(ADMIN_IP)).thenReturn(true);
        MockHttpServletRequest request = request("/api/admin/market-map/categories");
        request.addHeader("Authorization", "Bearer invalid-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(allowedIpAccessService);
    }

    @Test
    void apiRequestsWaitUntilTheStartupBackfillCompletes() throws Exception {
        LegacyCompatibilityBackfillState pendingBackfill = new LegacyCompatibilityBackfillState();
        AuthTokenFilter pendingFilter = new AuthTokenFilter(appJwtService, allowedIpAccessService, pendingBackfill);
        MockHttpServletResponse response = new MockHttpServletResponse();

        pendingFilter.doFilter(request("/api/map"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(allowedIpAccessService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/custom/preferences", "/api/auth/session"})
    void validCaptureTokenAuthenticatesGetRequestsAsAReadOnlyUser(String path) throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new AuthenticatedUserPrincipal(42L, Role.USER));
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    void invalidCaptureTokenIsRejectedWithUnauthorized() throws Exception {
        when(appJwtService.parseCaptureToken("capture-token")).thenReturn(null);
        MockHttpServletRequest request = captureRequest("GET", "/api/auth/session");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void captureTokenRejectsNonGetRequests(String method) throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest(method, "/api/custom/preferences");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/admin/market-map/categories",
                "/api/watch-stocks",
                "/api/watch-stocks/1",
            })
    void captureTokenRejectsAdminRoutes(String path) throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void captureTokenRejectsRefreshEvenAsAGetRequest() throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest("GET", "/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void captureTokenRejectsLogoutEvenAsAGetRequest() throws Exception {
        when(appJwtService.parseCaptureToken("capture-token"))
                .thenReturn(new AuthenticatedUserPrincipal(42L, Role.USER));
        MockHttpServletRequest request = captureRequest("GET", "/api/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void captureTokenHeaderIsIgnoredWhenReadingFromCookies() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/custom/preferences");
        request.setServletPath("/api/custom/preferences");
        request.setCookies(new Cookie("X-Capture-Token", "capture-token"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(appJwtService);
    }

    private MockHttpServletRequest captureRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader("X-Capture-Token", "capture-token");
        return request;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.addHeader("X-Real-IP", ADMIN_IP);
        return request;
    }
}
