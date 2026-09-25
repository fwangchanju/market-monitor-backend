package dev.eolmae.marketmonitor.domain.auth.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "user_refresh_token")
@Getter
public class UserRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 255)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected UserRefreshToken() {}

    public static UserRefreshToken create(UserAccount user, String tokenHash, LocalDateTime expiresAt) {
        var token = new UserRefreshToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return token;
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = LocalDateTime.now(Zone.KST.zoneId());
        }
    }

    public boolean isUsableAt(LocalDateTime time) {
        return revokedAt == null && expiresAt.isAfter(time);
    }
}
