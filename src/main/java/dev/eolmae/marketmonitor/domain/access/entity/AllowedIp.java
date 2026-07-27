package dev.eolmae.marketmonitor.domain.access.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.access.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "allowed_ip")
public class AllowedIp {

    @Id
    @Column(length = 45)
    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AllowedIp() {}

    public static AllowedIp create(String ip, Role role) {
        var entity = new AllowedIp();
        entity.ip = ip;
        entity.role = role;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void promoteToAdmin() {
        this.role = Role.ADMIN;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
