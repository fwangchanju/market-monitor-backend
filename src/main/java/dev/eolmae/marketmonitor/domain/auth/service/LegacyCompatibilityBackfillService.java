package dev.eolmae.marketmonitor.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles data written by the V1-compatible server after V2 was deployed. */
@Service
@RequiredArgsConstructor
public class LegacyCompatibilityBackfillService {

    private static final long LEGACY_OWNER_ID = 999999L;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void reconcile() {
        acquireIndustryUserSyncLock();
        synchronizeLegacyAliases();
        backfillNewIndustries();
        updateIndustryLinks();
        clearIndustryLinksForBlankNames();
        validateReconciledData();
    }

    private void acquireIndustryUserSyncLock() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, SignupInitializationService.INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });
    }

    private void synchronizeLegacyAliases() {
        jdbcTemplate.update("""
                INSERT INTO custom_stock_alias (user_id, stock_code, alias, created_at, updated_at)
                SELECT user_id, stock_code, alias, created_at, CURRENT_TIMESTAMP
                FROM custom_stock_sector
                WHERE user_id = ? AND alias IS NOT NULL
                ON CONFLICT (user_id, stock_code) DO UPDATE
                SET alias = EXCLUDED.alias, updated_at = CURRENT_TIMESTAMP
                """, LEGACY_OWNER_ID);
        jdbcTemplate.update("""
                DELETE FROM custom_stock_alias AS saved_alias
                USING custom_stock_sector AS assignment
                WHERE saved_alias.user_id = ?
                  AND assignment.user_id = saved_alias.user_id
                  AND assignment.stock_code = saved_alias.stock_code
                  AND assignment.alias IS NULL
                """, LEGACY_OWNER_ID);
    }

    private void backfillNewIndustries() {
        jdbcTemplate.update("""
                WITH inserted_industries AS (
                    INSERT INTO industry_info (name)
                    SELECT DISTINCT BTRIM(industry_name)
                    FROM stock_info
                    WHERE active
                      AND market_code IN ('0', '10')
                      AND industry_name IS NOT NULL
                      AND BTRIM(industry_name) <> ''
                    ON CONFLICT (name) DO NOTHING
                    RETURNING name
                )
                INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded)
                SELECT users.id, NULL, inserted_industries.name, 0, FALSE
                FROM users
                CROSS JOIN inserted_industries
                ON CONFLICT (user_id, name) DO NOTHING
                """);
    }

    private void updateIndustryLinks() {
        jdbcTemplate.update("""
                UPDATE stock_info AS stock
                SET industry_id = industry.id, updated_at = CURRENT_TIMESTAMP
                FROM industry_info AS industry
                WHERE stock.active
                  AND stock.market_code IN ('0', '10')
                  AND stock.industry_name IS NOT NULL
                  AND BTRIM(stock.industry_name) = industry.name
                  AND stock.industry_id IS DISTINCT FROM industry.id
                """);
    }

    private void clearIndustryLinksForBlankNames() {
        jdbcTemplate.update("""
                UPDATE stock_info
                SET industry_id = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE active
                  AND market_code IN ('0', '10')
                  AND (industry_name IS NULL OR BTRIM(industry_name) = '')
                  AND industry_id IS NOT NULL
                """);
    }

    private void validateReconciledData() {
        Long aliasMismatches = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM custom_stock_sector AS assignment
                WHERE assignment.user_id = ?
                  AND (
                      (assignment.alias IS NULL AND EXISTS (
                          SELECT 1 FROM custom_stock_alias AS saved_alias
                          WHERE saved_alias.user_id = assignment.user_id
                            AND saved_alias.stock_code = assignment.stock_code
                      ))
                      OR (assignment.alias IS NOT NULL AND NOT EXISTS (
                          SELECT 1 FROM custom_stock_alias AS saved_alias
                          WHERE saved_alias.user_id = assignment.user_id
                            AND saved_alias.stock_code = assignment.stock_code
                            AND saved_alias.alias = assignment.alias
                      ))
                  )
                """, Long.class, LEGACY_OWNER_ID);
        if (aliasMismatches == null || aliasMismatches != 0L) {
            throw new IllegalStateException("Legacy owner aliases could not be synchronized.");
        }

        Long industryMismatches = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM stock_info AS stock
                LEFT JOIN industry_info AS industry ON industry.id = stock.industry_id
                WHERE stock.active
                  AND stock.market_code IN ('0', '10')
                  AND (
                      ((stock.industry_name IS NULL OR BTRIM(stock.industry_name) = '')
                          AND stock.industry_id IS NOT NULL)
                      OR (stock.industry_name IS NOT NULL AND BTRIM(stock.industry_name) <> ''
                          AND (industry.id IS NULL OR industry.name <> BTRIM(stock.industry_name)))
                  )
                """, Long.class);
        if (industryMismatches == null || industryMismatches != 0L) {
            throw new IllegalStateException("Active stock industry links could not be synchronized.");
        }
    }
}
