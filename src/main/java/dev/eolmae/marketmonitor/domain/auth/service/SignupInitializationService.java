package dev.eolmae.marketmonitor.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupInitializationService {

    static final long INDUSTRY_USER_SYNC_LOCK = 941270361L;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void initialize(Long userId) {
        // pg_advisory_xact_lock — 가입·업종 전파를 직렬화하는 PostgreSQL 세션 락. JPQL/QueryDSL에는
        // 대응하는 문법이 없다.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });
        // INSERT ... SELECT + ON CONFLICT DO NOTHING — industry_info 전체를 신규 사용자의 기본
        // 카테고리로 한 번에 복제하는 집합 연산. QueryDSL은 INSERT ... SELECT를 지원하지 않는다.
        jdbcTemplate.update("""
                INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                SELECT ?, NULL, industry.name, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM industry_info industry
                ON CONFLICT (user_id, name) DO NOTHING
                """, userId);
        // INSERT ... SELECT + ON CONFLICT DO NOTHING — default_scale_threshold를 신규 사용자 기본값으로
        // 복제. 동일한 이유로 QueryDSL로 표현 불가.
        jdbcTemplate.update("""
                INSERT INTO custom_scale_threshold
                    (user_id, threshold_percent, color, color_label, created_at, updated_at)
                SELECT ?, threshold_percent, color, color_label, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM default_scale_threshold
                ON CONFLICT (user_id, threshold_percent) DO NOTHING
                """, userId);
        // INSERT ... SELECT + ON CONFLICT DO NOTHING — default_value_tier_threshold를 신규 사용자
        // 기본값으로 복제. 동일한 이유로 QueryDSL로 표현 불가.
        jdbcTemplate.update("""
                INSERT INTO custom_value_tier_threshold
                    (user_id, label, threshold_value, is_excluded_by_default, created_at, updated_at)
                SELECT ?, label, threshold_value, is_excluded_by_default, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM default_value_tier_threshold
                ON CONFLICT (user_id, label) DO NOTHING
                """, userId);
        // ON CONFLICT DO NOTHING + jsonb 리터럴('{}'::JSONB) — user_preference에 매핑된 JPA 엔티티가
        // 없고, JSONB 캐스팅도 JPQL/QueryDSL 문법이 아니다.
        jdbcTemplate.update(
                "INSERT INTO user_preference (user_id, payload) VALUES (?, '{}'::JSONB) ON CONFLICT (user_id) DO NOTHING",
                userId);
    }
}
