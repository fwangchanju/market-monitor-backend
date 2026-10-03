package dev.eolmae.marketry.domain.auth.config;

import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.service.AuthTokenFilter;
import dev.eolmae.marketry.domain.auth.service.OriginCheckFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    FilterRegistrationBean<AuthTokenFilter> authTokenFilterServletRegistration(AuthTokenFilter filter) {
        FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<OriginCheckFilter> originCheckFilterServletRegistration(OriginCheckFilter filter) {
        FilterRegistrationBean<OriginCheckFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain authSecurityFilterChain(
            HttpSecurity http, AuthTokenFilter authTokenFilter, OriginCheckFilter originCheckFilter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**", "/actuator/health")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/api/admin/**", "/api/watch-stocks", "/api/watch-stocks/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/custom/**")
                        .authenticated()
                        .requestMatchers("/api/profile", "/api/profile/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(originCheckFilter, AuthTokenFilter.class)
                .build();
    }
}
