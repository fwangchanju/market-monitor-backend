package dev.eolmae.marketmonitor.domain.custom.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(name = "custom_sector")
@Entity
@Getter
public class CustomSector {

    private static final Long LEGACY_OWNER_ID = 999999L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int depth;

    @Column(name = "is_excluded", nullable = false)
    private boolean isExcluded;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CustomSector() {}

    public static CustomSector createParent(Long userId, String name) {
        var entity = new CustomSector();
        entity.userId = userId;
        entity.name = name;
        entity.parentId = null;
        entity.depth = 0;
        entity.isExcluded = false;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public static CustomSector createParent(String name) {
        return createParent(LEGACY_OWNER_ID, name);
    }

    public static CustomSector createChild(Long userId, String name, CustomSector parent) {
        var entity = new CustomSector();
        entity.userId = userId;
        entity.name = name;
        entity.parentId = parent.id;
        entity.depth = parent.depth + 1;
        entity.isExcluded = false;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public static CustomSector createChild(String name, CustomSector parent) {
        return createChild(parent.userId, name, parent);
    }

    public static CustomSector createDefaultParent(Long userId, String name) {
        return createParent(userId, name);
    }

    public void tagSnapshot(Long snapshotId) {
        this.snapshotId = snapshotId;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void changeParent(Long parentId) {
        this.parentId = parentId;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void changeDepth(int depth) {
        this.depth = depth;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void exclude() {
        this.isExcluded = true;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void include() {
        this.isExcluded = false;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public boolean hasNoParent() {
        return parentId == null;
    }
}
