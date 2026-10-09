package dev.eolmae.marketry.domain.view.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MarketMapResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void 분류_갱신_시각을_옛_이름과_새_이름으로_함께_내려_보낸다() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 8, 15, 55, 0);
        MarketMapResponse response = new MarketMapResponse(null, List.of(), null, updatedAt);

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(json.has("classificationUpdatedAt")).isTrue();
        assertThat(json.has("taxonomyUpdatedAt")).isTrue();
        assertThat(json.get("taxonomyUpdatedAt")).isEqualTo(json.get("classificationUpdatedAt"));
    }

    @Test
    void 갱신_시각이_없으면_두_이름_모두_null이다() {
        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(MarketMapResponse.empty()));

        assertThat(json.get("classificationUpdatedAt").isNull()).isTrue();
        assertThat(json.get("taxonomyUpdatedAt").isNull()).isTrue();
    }
}
