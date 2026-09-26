package dev.eolmae.marketmonitor.domain.custom.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.common.exception.BadRequestException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomPreferenceService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> getPreferences() {
        Long userId = CurrentUser.requireId();
        // payload::TEXT — JSONB 캐스팅. user_preference에 매핑된 JPA 엔티티가 없고, JSONB 컬럼
        // 자체도 JPQL/QueryDSL 문법으로 다룰 수 없다.
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload::TEXT FROM user_preference WHERE user_id = ?", String.class, userId);
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }

    @Transactional
    public void replacePreferences(Map<String, Object> payload) {
        Long userId = CurrentUser.requireId();
        try {
            String json = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
            // CAST(? AS JSONB) — 위와 동일한 이유(엔티티 없음 + JSONB 캐스팅)로 JdbcTemplate을 쓴다.
            jdbcTemplate.update(
                    "UPDATE user_preference SET payload = CAST(? AS JSONB), updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    json,
                    userId);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }
}
