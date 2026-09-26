package dev.eolmae.marketmonitor.domain.auth.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.domain.custom.entity.UserPreference;
import dev.eolmae.marketmonitor.domain.custom.repository.UserPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SignupInitializationServiceTest {

    private static final long USER_ID = 41L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final UserPreferenceRepository userPreferenceRepository = mock(UserPreferenceRepository.class);
    private final SignupInitializationService service =
            new SignupInitializationService(jdbcTemplate, userPreferenceRepository);

    @Test
    void initialize_user_preference_행이_없으면_빈_행을_생성한다() {
        when(userPreferenceRepository.existsById(USER_ID)).thenReturn(false);

        service.initialize(USER_ID);

        verify(userPreferenceRepository).save(org.mockito.ArgumentMatchers.any(UserPreference.class));
    }

    // 재시도(가입 API 재호출 등)로 initialize가 두 번 불려도 이미 있는 user_preference 행을 덮어쓰지
    // 않는다 — 그 사이 사용자가 저장한 설정이 있으면 그걸 지우게 된다.
    @Test
    void initialize_user_preference_행이_이미_있으면_다시_만들지_않는다() {
        when(userPreferenceRepository.existsById(USER_ID)).thenReturn(true);

        service.initialize(USER_ID);

        verify(userPreferenceRepository, never()).save(org.mockito.ArgumentMatchers.any(UserPreference.class));
    }
}
