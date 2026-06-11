package dev.eolmae.marketmonitor.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    public static final String STOCK_INFO = "stock-info";
    public static final String WATCH_CODES = "watch-codes";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                new CaffeineCache(
                        STOCK_INFO, Caffeine.newBuilder().maximumSize(5000).build()),
                new CaffeineCache(
                        WATCH_CODES, Caffeine.newBuilder().maximumSize(500).build())));
        return manager;
    }
}
