package dev.eolmae.marketmonitor.domain.auth.service;

import dev.eolmae.marketmonitor.common.event.UserSignedUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupInitializationService {

    static final long INDUSTRY_USER_SYNC_LOCK = 941270361L;
    private static final long TEMPLATE_USER_ID = 1L;

    private final JdbcTemplate jdbcTemplate;

    @EventListener
    @Transactional
    public void onUserSignedUp(UserSignedUpEvent event) {
        Long userId = event.userId();
        // pg_advisory_xact_lock — 가입·업종·신규 종목 전파를 직렬화하는 PostgreSQL 세션 락. JPQL/QueryDSL에는
        // 대응하는 문법이 없다.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });
        // 섹터의 ID는 새로 발급한다. 이름은 사용자별 유일하므로 부모와 종목 배정을 이름으로 재연결한다.
        // snapshot_id는 과거 스냅샷을 복제하지 않으므로 비운다.
        jdbcTemplate.update("""
                INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                SELECT ?, NULL, source.name, source.depth, source.is_excluded, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM custom_sector source
                WHERE source.user_id = ?
                ON CONFLICT (user_id, name) DO NOTHING
                """, userId, TEMPLATE_USER_ID);
        jdbcTemplate.update("""
                UPDATE custom_sector target
                SET parent_id = target_parent.id
                FROM custom_sector source
                JOIN custom_sector source_parent ON source_parent.id = source.parent_id
                JOIN custom_sector target_parent ON target_parent.user_id = ?
                    AND target_parent.name = source_parent.name
                WHERE source.user_id = ?
                    AND target.user_id = ?
                    AND target.name = source.name
                    AND target.parent_id IS NULL
                """, userId, TEMPLATE_USER_ID, userId);
        jdbcTemplate.update("""
                INSERT INTO custom_stock_sector (user_id, stock_code, sector_id, created_at, updated_at)
                SELECT ?, source.stock_code, target_sector.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM custom_stock_sector source
                JOIN custom_sector source_sector ON source_sector.id = source.sector_id
                JOIN custom_sector target_sector ON target_sector.user_id = ?
                    AND target_sector.name = source_sector.name
                WHERE source.user_id = ?
                ON CONFLICT (user_id, stock_code) DO NOTHING
                """, userId, userId, TEMPLATE_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO custom_stock_alias (user_id, stock_code, alias, created_at, updated_at)
                SELECT ?, source.stock_code, source.alias, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM custom_stock_alias source
                WHERE source.user_id = ?
                ON CONFLICT (user_id, stock_code) DO NOTHING
                """, userId, TEMPLATE_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO custom_scale_threshold
                    (user_id, threshold_percent, color, color_label, created_at, updated_at)
                SELECT ?, source.threshold_percent, source.color, source.color_label,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM custom_scale_threshold source
                WHERE source.user_id = ?
                ON CONFLICT (user_id, threshold_percent) DO NOTHING
                """, userId, TEMPLATE_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO custom_value_tier_threshold
                    (user_id, label, threshold_value, is_excluded_by_default, created_at, updated_at)
                SELECT ?, source.label, source.threshold_value, source.is_excluded_by_default,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM custom_value_tier_threshold source
                WHERE source.user_id = ?
                ON CONFLICT (user_id, label) DO NOTHING
                """, userId, TEMPLATE_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO user_preference (user_id, payload, updated_at)
                SELECT ?, COALESCE((SELECT payload FROM user_preference WHERE user_id = ?), '{}'::jsonb),
                    CURRENT_TIMESTAMP
                ON CONFLICT (user_id) DO NOTHING
                """, userId, TEMPLATE_USER_ID);
    }
}
