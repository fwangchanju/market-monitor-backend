package dev.eolmae.marketmonitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.common.cache.CacheKey;
import jakarta.persistence.EntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ApplicationConfig {

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager(CacheKey.STOCK_INFO, CacheKey.WATCH_STOCK);
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
