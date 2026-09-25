package dev.eolmae.marketmonitor.domain.auth.service;

import dev.eolmae.marketmonitor.common.event.IndustryInfoCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IndustrySectorPropagationService {

    private final JdbcTemplate jdbcTemplate;

    @EventListener
    @Transactional
    public void onIndustryInfoCreated(IndustryInfoCreatedEvent event) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, SignupInitializationService.INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });
        jdbcTemplate.update("""
                INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                SELECT users.id, NULL, ?, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM users
                ON CONFLICT (user_id, name) DO NOTHING
                """, event.name());
    }
}
