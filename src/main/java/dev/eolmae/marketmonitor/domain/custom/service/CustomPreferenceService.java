package dev.eolmae.marketmonitor.domain.custom.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.common.exception.BadRequestException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
import dev.eolmae.marketmonitor.domain.custom.entity.UserPreference;
import dev.eolmae.marketmonitor.domain.custom.repository.UserPreferenceRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> getPreferences() {
        Long userId = CurrentUser.requireId();
        return userPreferenceRepository
                .findById(userId)
                .map(preference -> parsePayload(preference.getPayload()))
                .orElseGet(Map::of);
    }

    @Transactional
    public void replacePreferences(Map<String, Object> payload) {
        Long userId = CurrentUser.requireId();
        String json = toJson(payload == null ? Map.of() : payload);
        UserPreference preference =
                userPreferenceRepository.findById(userId).orElseGet(() -> UserPreference.createEmpty(userId));
        preference.overwrite(json);
        userPreferenceRepository.save(preference);
    }

    private Map<String, Object> parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, e);
        }
    }
}
