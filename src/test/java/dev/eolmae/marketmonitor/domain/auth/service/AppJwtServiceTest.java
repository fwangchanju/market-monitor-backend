package dev.eolmae.marketmonitor.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.domain.access.enums.Role;
import dev.eolmae.marketmonitor.domain.auth.properties.AuthProperties;
import org.junit.jupiter.api.Test;

class AppJwtServiceTest {

    @Test
    void issued_access_token_round_trips_user_identity_and_role() {
        AppJwtService service = serviceWithSecret("0123456789abcdef0123456789abcdef");
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(73L, Role.USER);

        String token = service.issueAccessToken(principal);

        assertThat(service.parse(token)).isEqualTo(principal);
    }

    @Test
    void changed_signature_is_rejected() {
        AppJwtService service = serviceWithSecret("0123456789abcdef0123456789abcdef");
        String[] parts = service.issueAccessToken(new AuthenticatedUserPrincipal(73L, Role.ADMIN))
                .split("\\.", -1);
        String signature = parts[2];
        parts[2] = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);

        assertThat(service.parse(String.join(".", parts))).isNull();
    }

    @Test
    void short_signing_secret_is_rejected() {
        AppJwtService service = serviceWithSecret("short");

        assertThatThrownBy(() -> service.issueAccessToken(new AuthenticatedUserPrincipal(73L, Role.USER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private AppJwtService serviceWithSecret(String secret) {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret(secret);
        return new AppJwtService(properties, new ObjectMapper());
    }
}
