package dev.eolmae.marketmonitor.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.eolmae.marketmonitor.common.event.UserSignedUpEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class SignupInitializationServiceTest {

    private static final long USER_ID = 41L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SignupInitializationService service = new SignupInitializationService(jdbcTemplate);

    @Test
    void 가입시_템플릿의_현재_커스텀_데이터를_모두_복제한다() {
        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(7)).update(sql.capture(), any(Object[].class));
        List<String> statements = sql.getAllValues();
        assertThat(statements.get(0)).contains("INSERT INTO custom_sector", "source.depth", "source.is_excluded");
        assertThat(statements.get(1)).contains("UPDATE custom_sector", "source_parent.name", "target_parent.name");
        assertThat(statements.get(2))
                .contains("INSERT INTO custom_stock_sector", "source_sector.name", "target_sector.name");
        assertThat(statements.get(3)).contains("INSERT INTO custom_stock_alias", "source.alias");
        assertThat(statements.get(4)).contains("INSERT INTO custom_scale_threshold", "source.color_label");
        assertThat(statements.get(5))
                .contains("INSERT INTO custom_value_tier_threshold", "source.is_excluded_by_default");
        assertThat(statements.get(6)).contains("INSERT INTO user_preference", "'{}'::jsonb");
        assertThat(statements.get(0)).doesNotContain("snapshot_id");
        assertThat(statements.get(6)).contains("ON CONFLICT (user_id) DO NOTHING");
    }
}
