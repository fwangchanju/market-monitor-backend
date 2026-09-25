package dev.eolmae.marketmonitor.domain.custom.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "custom_stock_alias")
@Getter
public class CustomStockAlias {

    @EmbeddedId
    private CustomStockAliasId id;

    @Column(nullable = false, length = 50)
    private String alias;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CustomStockAlias() {}

    public static CustomStockAlias create(Long userId, String stockCode, String alias) {
        var entity = new CustomStockAlias();
        entity.id = new CustomStockAliasId(userId, stockCode);
        entity.alias = alias;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public String getStockCode() {
        return id.getStockCode();
    }

    public void updateAlias(String alias) {
        this.alias = alias;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
