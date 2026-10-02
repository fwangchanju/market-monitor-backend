package dev.eolmae.marketmonitor.domain.auth.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "user_profile")
@Getter
public class UserProfile {

    @Id
    private Long userId;

    @Column(length = 12)
    private String nickname;

    @Column(columnDefinition = "bytea")
    private byte[] image;

    private LocalDateTime imageUpdatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserProfile() {}

    public UserProfile(Long userId) {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void changeImage(byte[] image) {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        this.image = image;
        this.imageUpdatedAt = now;
        this.updatedAt = now;
    }

    public void removeImage() {
        this.image = null;
        this.imageUpdatedAt = null;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
