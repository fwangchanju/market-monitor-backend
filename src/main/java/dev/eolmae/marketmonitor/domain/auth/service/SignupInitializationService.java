package dev.eolmae.marketmonitor.domain.auth.service;

import dev.eolmae.marketmonitor.domain.custom.entity.UserPreference;
import dev.eolmae.marketmonitor.domain.custom.repository.UserPreferenceRepository;
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
    private final UserPreferenceRepository userPreferenceRepository;

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
        // 섹터로 한 번에 복제하는 집합 연산. QueryDSL은 INSERT ... SELECT를 지원하지 않는다.
        jdbcTemplate.update("""
                INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                SELECT ?, NULL, industry.name, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM industry_info industry
                ON CONFLICT (user_id, name) DO NOTHING
                """, userId);
        if (!userPreferenceRepository.existsById(userId)) {
            userPreferenceRepository.save(UserPreference.createEmpty(userId));
        }
    }
}
