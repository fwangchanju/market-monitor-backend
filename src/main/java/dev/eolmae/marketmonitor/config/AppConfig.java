package dev.eolmae.marketmonitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.common.cache.CacheKey;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager(CacheKey.STOCK_INFO, CacheKey.WATCH_STOCK);
    }

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
