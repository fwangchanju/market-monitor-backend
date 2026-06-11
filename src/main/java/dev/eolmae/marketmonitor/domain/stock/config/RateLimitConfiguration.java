package dev.eolmae.marketmonitor.domain.stock.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfiguration {

    @Bean
    @SuppressWarnings("UnstableApiUsage")
    public RateLimiter rateLimiter() {
        return RateLimiter.create(5.0);
    }
}
