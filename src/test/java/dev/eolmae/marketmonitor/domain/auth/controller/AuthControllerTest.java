package dev.eolmae.marketmonitor.domain.auth.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eolmae.marketmonitor.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketmonitor.domain.auth.enums.Role;
import dev.eolmae.marketmonitor.domain.auth.properties.AuthProperties;
import dev.eolmae.marketmonitor.domain.auth.service.AppJwtService;
import dev.eolmae.marketmonitor.domain.auth.service.AuthService;
import dev.eolmae.marketmonitor.domain.auth.service.AuthService.IssuedTokens;
import dev.eolmae.marketmonitor.domain.auth.service.AuthenticatedUserPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private static final AuthenticatedUserPrincipal PRINCIPAL = new AuthenticatedUserPrincipal(42L, Role.USER);

    private final AuthService authService = mock(AuthService.class);
    private final AppJwtService appJwtService = mock(AppJwtService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new AuthController(authService, new AuthProperties(), appJwtService))
            .build();

    @BeforeEach
    void setAuthenticatedUser() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void session_returnsJsonResponse() throws Exception {
        when(authService.session(PRINCIPAL))
                .thenReturn(new AuthSessionResponse(true, 42L, "user@example.com", Role.USER));

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void refresh_returnsJsonResponseAndRotatesCookies() throws Exception {
        IssuedTokens tokens = new IssuedTokens("access-token", "rotated-refresh-token");
        when(authService.refresh("refresh-token")).thenReturn(tokens);
        when(appJwtService.parse("access-token")).thenReturn(PRINCIPAL);
        when(authService.session(PRINCIPAL))
                .thenReturn(new AuthSessionResponse(true, 42L, "user@example.com", Role.USER));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("mm_refresh", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(header().stringValues(
                                "Set-Cookie",
                                containsInAnyOrder(
                                        containsString("mm_access=access-token"),
                                        containsString("mm_refresh=rotated-refresh-token"))));
    }
}
