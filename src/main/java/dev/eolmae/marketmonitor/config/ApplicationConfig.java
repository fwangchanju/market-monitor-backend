package dev.eolmae.marketmonitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketmonitor.common.cache.CacheKey;
import jakarta.persistence.EntityManager;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ApplicationConfig {

    public static final String CACHE_MANAGER = "cacheManager";
    public static final String ACCESS_CACHE_MANAGER = "accessCacheManager";

    // 외부 HTTPS 콜드 TLS 핸드셰이크(하루 첫 호출 등)엔 3초가 너무 빡빡해서 10초로 늘렸다 — 그래도
    // 무한 대기로 스케줄러가 전면 정지되는 건 충분히 막는다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean(CACHE_MANAGER)
    @Primary
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

    /** KrxCrawler 등 나머지 전부가 쓰는 기본 클라이언트. @Qualifier 없이 주입받는 곳은 전부 이 빈이다. */
    @Bean
    @Primary
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(10)))
                .build();
    }

    @Bean
    public RestClient kiwoomRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(10)))
                .build();
    }

    @Bean
    public RestClient telegramRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(30)))
                .build();
    }

    // page.goto 30초 + waitForSelector 15초(containers/renderer/server.js)를 감안한 값 — 짧게 걸면
    // 일일 리포트 스크린샷이 항상 실패한다.
    @Bean
    public RestClient rendererRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(90)))
                .build();
    }

    private ClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
