package dev.eolmae.marketry.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.auth.service.AuthTokenFilter;
import dev.eolmae.marketry.domain.auth.service.OriginCheckFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

class AuthConfigurationTest {

    private final AuthConfiguration configuration = new AuthConfiguration();

    @Test
    void securityChainFiltersAreNotAlsoRegisteredByTheServletContainer() {
        FilterRegistrationBean<AuthTokenFilter> authRegistration =
                configuration.authTokenFilterServletRegistration(mock(AuthTokenFilter.class));
        FilterRegistrationBean<OriginCheckFilter> originRegistration =
                configuration.originCheckFilterServletRegistration(mock(OriginCheckFilter.class));

        assertThat(authRegistration.isEnabled()).isFalse();
        assertThat(originRegistration.isEnabled()).isFalse();
    }

    // DB 없이는 @SpringBootTest를 못 쓰므로(docs/rules/testing.md), @EnableWebSecurity만 올린 최소
    // 컨텍스트에서 실제 SecurityFilterChain을 만들어 FilterChainProxy로 직접 호출한다.
    @Test
    void 인증_없이_custom_API를_요청하면_401을_응답한다() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
            FilterChainProxy filterChainProxy = new FilterChainProxy(chain);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/custom/sectors");
            request.setServletPath("/api/custom/sectors");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filterChainProxy.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }
    }

    @Test
    void 인증_없이_프로필_API를_요청하면_모두_401을_응답한다() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            FilterChainProxy filterChainProxy = new FilterChainProxy(context.getBean(SecurityFilterChain.class));

            // 쓰기 요청은 Origin 검사(OriginCheckFilter)가 인가보다 먼저라서 허용된 Origin을 넣어야 401이 나온다.
            assertThat(statusOf(filterChainProxy, "GET", "/api/profile", null)).isEqualTo(401);
            assertThat(statusOf(filterChainProxy, "GET", "/api/profile/image", null))
                    .isEqualTo(401);
            assertThat(statusOf(filterChainProxy, "PUT", "/api/profile/nickname", "http://localhost"))
                    .isEqualTo(401);
            assertThat(statusOf(filterChainProxy, "PUT", "/api/profile/image", "http://localhost"))
                    .isEqualTo(401);
            assertThat(statusOf(filterChainProxy, "DELETE", "/api/profile/image", "http://localhost"))
                    .isEqualTo(401);
        }
    }

    private int statusOf(FilterChainProxy filterChainProxy, String method, String path, String origin)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterChainProxy.doFilter(request, response, new MockFilterChain());

        return response.getStatus();
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfig {

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AppJwtService appJwtService(AuthProperties authProperties, ObjectMapper objectMapper) {
            return new AppJwtService(authProperties, objectMapper);
        }

        @Bean
        AuthTokenFilter authTokenFilter(AppJwtService appJwtService) {
            return new AuthTokenFilter(appJwtService);
        }

        @Bean
        OriginCheckFilter originCheckFilter(AuthProperties authProperties) {
            return new OriginCheckFilter(authProperties);
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http, AuthTokenFilter authTokenFilter, OriginCheckFilter originCheckFilter)
                throws Exception {
            return new AuthConfiguration().authSecurityFilterChain(http, authTokenFilter, originCheckFilter);
        }
    }
}
