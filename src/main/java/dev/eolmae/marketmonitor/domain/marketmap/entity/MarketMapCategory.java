package dev.eolmae.marketmonitor.domain.marketmap.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(name = "market_map_category")
@Entity
@Getter
public class MarketMapCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "version_id")
    private Long versionId;

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

    protected MarketMapCategory() {}

    public static MarketMapCategory createParent(String name) {
        var entity = new MarketMapCategory();
        entity.name = name;
        entity.parentId = null;
        entity.depth = 0;
        entity.isExcluded = false;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public static MarketMapCategory createChild(String name, MarketMapCategory parent) {
        var entity = new MarketMapCategory();
        entity.name = name;
        entity.parentId = parent.id;
        entity.depth = parent.depth + 1;
        entity.isExcluded = false;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void tagVersion(Long versionId) {
        this.versionId = versionId;
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
}
