package dev.eolmae.marketmonitor.config;

import dev.eolmae.marketmonitor.common.cache.CacheKey;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {

        return new CaffeineCacheManager(CacheKey.STOCK_INFO, CacheKey.WATCH_STOCK);
    }
}
