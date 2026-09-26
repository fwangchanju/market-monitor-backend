package dev.eolmae.marketmonitor.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserRefreshTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 12, 0);

    @Test
    void 교체_직후에는_사용_가능하다() {
        UserRefreshToken token = createToken(NOW.plusDays(1));

        token.replace();

        assertThat(token.isUsableAt(token.getReplacedAt())).isTrue();
    }

    @Test
    void 교체_30초가_지나면_사용_불가능하다() {
        UserRefreshToken token = createToken(NOW.plusDays(1));

        token.replace();

        LocalDateTime after30Seconds = token.getReplacedAt().plusSeconds(30);
        assertThat(token.isUsableAt(after30Seconds)).isFalse();
    }

    @Test
    void replace를_두_번_호출해도_첫_교체_시각을_유지한다() {
        UserRefreshToken token = createToken(NOW.plusDays(1));

        token.replace();
        LocalDateTime firstReplacedAt = token.getReplacedAt();
        token.replace();

        assertThat(token.getReplacedAt()).isEqualTo(firstReplacedAt);
    }

    @Test
    void revoke된_토큰은_교체_여부와_무관하게_사용_불가능하다() {
        UserRefreshToken token = createToken(NOW.plusDays(1));

        token.revoke();

        assertThat(token.isUsableAt(token.getRevokedAt())).isFalse();
    }

    @Test
    void 만료된_토큰은_사용_불가능하다() {
        UserRefreshToken token = createToken(NOW.minusSeconds(1));

        assertThat(token.isUsableAt(NOW)).isFalse();
    }

    private UserRefreshToken createToken(LocalDateTime expiresAt) {
        return UserRefreshToken.create(mock(UserAccount.class), "hash", expiresAt);
    }
}
