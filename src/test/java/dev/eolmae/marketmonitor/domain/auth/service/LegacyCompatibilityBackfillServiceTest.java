package dev.eolmae.marketmonitor.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyCompatibilityBackfillServiceTest {

    private static final long LEGACY_OWNER_ID = 999999L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final LegacyCompatibilityBackfillService service = new LegacyCompatibilityBackfillService(jdbcTemplate);

    @Test
    void reconcileUsesOwnerScopedAliasUpsertsAndNewIndustryOnlySectorPropagation() {
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(), eq(Long.class), eq(LEGACY_OWNER_ID)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(), eq(Long.class)))
                .thenReturn(0L);

        service.reconcile();

        verify(jdbcTemplate)
                .update(
                        argThat(sql -> sql.contains("FROM custom_stock_sector")
                                && sql.contains("WHERE user_id = ? AND alias IS NOT NULL")
                                && sql.contains("ON CONFLICT (user_id, stock_code) DO UPDATE")),
                        eq(LEGACY_OWNER_ID));
        verify(jdbcTemplate)
                .update(
                        argThat(sql -> sql.contains("DELETE FROM custom_stock_alias")
                                && sql.contains("assignment.alias IS NULL")),
                        eq(LEGACY_OWNER_ID));
        verify(jdbcTemplate)
                .update(org.mockito.ArgumentMatchers.<String>argThat(sql -> sql.contains("INSERT INTO industry_info")
                        && sql.contains("ON CONFLICT (name) DO NOTHING")
                        && sql.contains("CROSS JOIN inserted_industries")
                        && sql.contains("ON CONFLICT (user_id, name) DO NOTHING")));
        verify(jdbcTemplate)
                .update(org.mockito.ArgumentMatchers.<String>argThat(sql -> sql.contains("UPDATE stock_info AS stock")
                        && sql.contains("stock.industry_id IS DISTINCT FROM industry.id")));
        verify(jdbcTemplate)
                .update(org.mockito.ArgumentMatchers.<String>argThat(sql -> sql.contains("SET industry_id = NULL")
                        && sql.contains("industry_name IS NULL OR BTRIM(industry_name) = ''")));
    }

    @Test
    void reconcileFailsStartupWhenTheAliasBackfillPostconditionIsNotMet() {
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(), eq(Long.class), eq(LEGACY_OWNER_ID)))
                .thenReturn(1L);

        assertThatThrownBy(service::reconcile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Legacy owner aliases could not be synchronized.");
    }
}
