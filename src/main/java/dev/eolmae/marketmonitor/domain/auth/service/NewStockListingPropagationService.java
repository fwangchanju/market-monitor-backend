package dev.eolmae.marketmonitor.domain.auth.service;

import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewStockListingPropagationService {

    private static final String LISTING_SECTOR_NAME = "신규상장";

    private final JdbcTemplate jdbcTemplate;

    @EventListener
    @Transactional
    public void onStockInfoSynced(StockInfoSyncedEvent event) {
        if (event.stockCodes().isEmpty()) {
            return;
        }

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
                """, LISTING_SECTOR_NAME);

        jdbcTemplate.update("""
                INSERT INTO custom_stock_sector (user_id, stock_code, sector_id, created_at, updated_at)
                SELECT users.id, listed.stock_code, sector.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM users
                CROSS JOIN unnest(?::varchar[]) AS listed(stock_code)
                JOIN custom_sector sector ON sector.user_id = users.id AND sector.name = ?
                ON CONFLICT (user_id, stock_code) DO NOTHING
                """, (PreparedStatementSetter) statement -> {
            statement.setArray(
                    1,
                    statement
                            .getConnection()
                            .createArrayOf("varchar", event.stockCodes().toArray()));
            statement.setString(2, LISTING_SECTOR_NAME);
        });
    }
}
