package dev.eolmae.marketry.domain.stock.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IntegratedSessionsTest {
    @Test
    void 시간대와누락세션을JSON왕복에서보존한다() {
        var mapper = JsonMapper.builder().build();
        var start = OffsetDateTime.parse("2025-11-13T10:00:00+09:00");
        var end = OffsetDateTime.parse("2025-11-13T16:30:00+09:00");
        var integrated = new IntegratedSessions(null, new TradingSession(start, null, null, end), null);
        var restored = mapper.readValue(mapper.writeValueAsString(integrated), IntegratedSessions.class);
        assertThat(restored).isEqualTo(integrated);
        assertThat(restored.regularMarket().startTime().getOffset()).isEqualTo(start.getOffset());
    }
}
