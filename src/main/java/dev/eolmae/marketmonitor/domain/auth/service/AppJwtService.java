package dev.eolmae.marketmonitor.domain.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.domain.access.enums.Role;
import dev.eolmae.marketmonitor.domain.auth.properties.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppJwtService {

    private static final String ISSUER = "market-monitor";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long ACCESS_TOKEN_SECONDS = 15 * 60;

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public String issueAccessToken(AuthenticatedUserPrincipal user) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", user.userId().toString());
        claims.put("role", user.role().name());
        claims.put("iat", now);
        claims.put("exp", now + ACCESS_TOKEN_SECONDS);
        String unsigned = encode(header) + "." + encode(claims);
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(unsigned));
    }

    public AuthenticatedUserPrincipal parse(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }
            String unsigned = parts[0] + "." + parts[1];
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(signature, sign(unsigned))) {
                return null;
            }
            Map<String, Object> header = decode(parts[0]);
            Map<String, Object> claims = decode(parts[1]);
            if (!"HS256".equals(header.get("alg")) || !ISSUER.equals(claims.get("iss"))) {
                return null;
            }
            Object subject = claims.get("sub");
            Object role = claims.get("role");
            Object expiresAt = claims.get("exp");
            if (!(subject instanceof String userId)
                    || !(role instanceof String roleName)
                    || !(expiresAt instanceof Number expiry)
                    || expiry.longValue() <= Instant.now().getEpochSecond()) {
                return null;
            }
            return new AuthenticatedUserPrincipal(Long.parseLong(userId), Role.valueOf(roleName));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String encode(Object value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 생성에 실패했습니다.", e);
        }
    }

    private Map<String, Object> decode(String encoded) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        return objectMapper.readValue(bytes, new TypeReference<>() {});
    }

    private byte[] sign(String value) {
        try {
            byte[] key = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) {
                throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 32 bytes.");
            }
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명을 생성할 수 없습니다.", e);
        }
    }
}
