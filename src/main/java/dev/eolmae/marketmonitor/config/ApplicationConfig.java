package dev.eolmae.marketmonitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.common.cache.CacheKey;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class ApplicationConfig {

    public static final String CACHE_MANAGER = "cacheManager";
    public static final String ACCESS_CACHE_MANAGER = "accessCacheManager";

    @Primary
    @Bean(CACHE_MANAGER)
    public CacheManager cacheManager() {
        return new CaffeineCacheManager(CacheKey.STOCK_INFO, CacheKey.WATCH_STOCK);
    }

    /** IP 화이트리스트 체크용 캐시. 수동 DB 편집 반영을 위한 짧은 TTL(10초)만 두고, 등록/삭제 시 즉시 evict됨. */
    @Bean(ACCESS_CACHE_MANAGER)
    public CacheManager accessCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CacheKey.ALLOWED_IP);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(10)));
        return manager;
    }

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
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
