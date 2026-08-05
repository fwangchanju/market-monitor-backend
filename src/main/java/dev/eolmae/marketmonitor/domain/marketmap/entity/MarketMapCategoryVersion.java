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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Table(name = "market_map_category_version")
@Entity
@Getter
public class MarketMapCategoryVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "snapshot_json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String snapshotJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MarketMapCategoryVersion() {}

    public static MarketMapCategoryVersion create(String label, String snapshotJson) {
        var entity = new MarketMapCategoryVersion();
        entity.label = label;
        entity.snapshotJson = snapshotJson;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void overwrite(String label, String snapshotJson) {
        this.label = label;
        this.snapshotJson = snapshotJson;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
