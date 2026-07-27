package dev.eolmae.marketmonitor.domain.access.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "admin_token")
public class AdminToken {

    @Id
    @Column(length = 64)
    private String token;

    @Column(name = "last_ip", length = 45)
    private String lastIp;

    @Column(length = 50)
    private String label;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AdminToken() {}

    public static AdminToken create(String token) {
        var entity = new AdminToken();
        entity.token = token;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void updateLastIp(String ip) {
        this.lastIp = ip;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
