package dev.eolmae.marketmonitor.domain.auth.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.auth.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String issuer;

    @Column(length = 255)
    private String sub;

    @Column(length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserAccount() {}

    public void updateEmail(String email) {
        this.email = email;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
