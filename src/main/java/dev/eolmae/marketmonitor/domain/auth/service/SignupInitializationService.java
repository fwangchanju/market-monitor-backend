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
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });
        jdbcTemplate.update("""
                INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                SELECT ?, NULL, industry.name, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM industry_info industry
                ON CONFLICT (user_id, name) DO NOTHING
                """, userId);
        jdbcTemplate.update("""
                INSERT INTO custom_scale_threshold
                    (user_id, threshold_percent, color, color_label, created_at, updated_at)
                SELECT ?, threshold_percent, color, color_label, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM default_scale_threshold
                ON CONFLICT (user_id, threshold_percent) DO NOTHING
                """, userId);
        jdbcTemplate.update("""
                INSERT INTO custom_value_tier_threshold
                    (user_id, label, threshold_value, is_excluded_by_default, created_at, updated_at)
                SELECT ?, label, threshold_value, is_excluded_by_default, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM default_value_tier_threshold
                ON CONFLICT (user_id, label) DO NOTHING
                """, userId);
        jdbcTemplate.update(
                "INSERT INTO user_preference (user_id, payload) VALUES (?, '{}'::JSONB) ON CONFLICT (user_id) DO NOTHING",
                userId);
    }
}
