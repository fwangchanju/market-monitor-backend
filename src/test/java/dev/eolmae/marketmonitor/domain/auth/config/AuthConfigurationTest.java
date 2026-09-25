package dev.eolmae.marketmonitor.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.eolmae.marketmonitor.domain.auth.service.AuthTokenFilter;
import dev.eolmae.marketmonitor.domain.auth.service.OriginCheckFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

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
}
