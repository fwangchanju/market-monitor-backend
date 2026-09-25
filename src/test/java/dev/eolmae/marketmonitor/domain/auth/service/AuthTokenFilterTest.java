package dev.eolmae.marketmonitor.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.domain.access.enums.Role;
import dev.eolmae.marketmonitor.domain.access.service.AllowedIpAccessService;
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
    private final AuthTokenFilter filter = new AuthTokenFilter(appJwtService, allowedIpAccessService);

    @BeforeEach
    void clearSecurityContextBeforeEach() {
        SecurityContextHolder.clearContext();
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

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.addHeader("X-Real-IP", ADMIN_IP);
        return request;
    }
}
