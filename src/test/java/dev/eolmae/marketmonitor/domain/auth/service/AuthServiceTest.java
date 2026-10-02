package dev.eolmae.marketmonitor.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.auth.dto.AuthSessionResponse;
import dev.eolmae.marketmonitor.domain.auth.dto.ProfileResponse;
import dev.eolmae.marketmonitor.domain.auth.entity.UserAccount;
import dev.eolmae.marketmonitor.domain.auth.entity.UserRefreshToken;
import dev.eolmae.marketmonitor.domain.auth.enums.Role;
import dev.eolmae.marketmonitor.domain.auth.properties.AuthProperties;
import dev.eolmae.marketmonitor.domain.auth.repository.UserAccountRepository;
import dev.eolmae.marketmonitor.domain.auth.repository.UserRefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private final RestClient restClient = mock(RestClient.class);
    private final AuthProperties authProperties = new AuthProperties();
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final UserRefreshTokenRepository userRefreshTokenRepository = mock(UserRefreshTokenRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AppJwtService appJwtService = mock(AppJwtService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final UserProfileService userProfileService = mock(UserProfileService.class);

    private final AuthService authService = new AuthService(
            restClient,
            authProperties,
            userAccountRepository,
            userRefreshTokenRepository,
            eventPublisher,
            appJwtService,
            jdbcTemplate,
            userProfileService);

    @Test
    void refresh는_옛_토큰을_폐기가_아니라_교체한다() {
        UserRefreshToken token = usableTokenOf(userAccount(1L));
        when(userRefreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(appJwtService.issueAccessToken(any())).thenReturn("access-token");

        authService.refresh("raw-refresh-token");

        verify(token, times(1)).replace();
        verify(token, never()).revoke();
    }

    @Test
    void 교체_유예_시간_안에_같은_토큰이_다시_오면_새_토큰을_발급한다() {
        UserRefreshToken token = usableTokenOf(userAccount(1L));
        when(userRefreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(appJwtService.issueAccessToken(any())).thenReturn("access-token");

        AuthService.IssuedTokens issuedTokens = authService.refresh("raw-refresh-token");

        assertThat(issuedTokens.accessToken()).isEqualTo("access-token");
        assertThat(issuedTokens.refreshToken()).isNotBlank();
        verify(userRefreshTokenRepository, times(1)).save(any(UserRefreshToken.class));
    }

    @Test
    void 갱신_불가능한_토큰이면_401을_던진다() {
        UserRefreshToken token = mock(UserRefreshToken.class);
        when(token.isUsableAt(any())).thenReturn(false);
        when(userRefreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh("raw-refresh-token")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void logout은_토큰_조회_폐기_일괄_폐기_순서로_호출한다() {
        UserAccount user = userAccount(7L);
        UserRefreshToken token = mock(UserRefreshToken.class);
        when(token.getUser()).thenReturn(user);
        when(userRefreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        authService.logout("raw-refresh-token");

        InOrder inOrder = Mockito.inOrder(userRefreshTokenRepository, token);
        inOrder.verify(userRefreshTokenRepository).findByTokenHash(any());
        inOrder.verify(token).revoke();
        inOrder.verify(userRefreshTokenRepository).revokeTokensCreatedOrReplacedSince(eq(7L), any(), any());
    }

    @Test
    void logout시_토큰이_없으면_일괄_폐기를_호출하지_않는다() {
        when(userRefreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout("raw-refresh-token");

        verify(userRefreshTokenRepository, never()).revokeTokensCreatedOrReplacedSince(anyLong(), any(), any());
    }

    @Test
    void cleanupRefreshTokens는_now에서_하루_전_cutoff로_삭제_쿼리를_호출한다() {
        authService.cleanupRefreshTokens();

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRefreshTokenRepository).deleteExpiredOrStaleTokens(nowCaptor.capture(), cutoffCaptor.capture());

        assertThat(cutoffCaptor.getValue()).isEqualTo(nowCaptor.getValue().minusDays(1));
    }

    @Test
    void loginAsForDevelopment은_소유자_계정이_있으면_토큰을_발급한다() {
        UserAccount owner = userAccount(999999L);
        when(userAccountRepository.findById(999999L)).thenReturn(Optional.of(owner));
        when(appJwtService.issueAccessToken(any())).thenReturn("access-token");

        AuthService.IssuedTokens issuedTokens = authService.loginAsForDevelopment(999999L);

        assertThat(issuedTokens.accessToken()).isEqualTo("access-token");
        assertThat(issuedTokens.refreshToken()).isNotBlank();
        verify(userRefreshTokenRepository, times(1)).save(any(UserRefreshToken.class));
    }

    @Test
    void loginAsForDevelopment은_소유자_계정이_없으면_NotFoundException을_던진다() {
        when(userAccountRepository.findById(999999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginAsForDevelopment(999999L)).isInstanceOf(NotFoundException.class);
        verify(userRefreshTokenRepository, never()).save(any());
    }

    @Test
    void session은_프로필의_닉네임과_사진_버전을_함께_담는다() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(42L, Role.USER);
        UserAccount user = userAccount(42L);
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userProfileService.getProfile(42L)).thenReturn(new ProfileResponse("마켓러", true, 1_700_000_000_000L));

        AuthSessionResponse session = authService.session(principal);

        assertThat(session.authenticated()).isTrue();
        assertThat(session.nickname()).isEqualTo("마켓러");
        assertThat(session.profileImageVersion()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void session은_프로필이_없으면_닉네임과_사진_버전이_null이다() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(42L, Role.USER);
        UserAccount user = userAccount(42L);
        when(userAccountRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userProfileService.getProfile(42L)).thenReturn(ProfileResponse.empty());

        AuthSessionResponse session = authService.session(principal);

        assertThat(session.nickname()).isNull();
        assertThat(session.profileImageVersion()).isNull();
    }

    private UserRefreshToken usableTokenOf(UserAccount user) {
        UserRefreshToken token = mock(UserRefreshToken.class);
        when(token.isUsableAt(any())).thenReturn(true);
        when(token.getUser()).thenReturn(user);
        return token;
    }

    private UserAccount userAccount(Long id) {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(Role.USER);
        return user;
    }
}
