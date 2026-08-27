package dev.eolmae.marketmonitor.domain.marketmap.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.marketmap.enums.ColorLabel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(name = "market_map_scale_threshold")
@Entity
@Getter
public class MarketMapScaleThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 부호로 side를 표현한다(음수=하락, 0=기준, 양수=상승) — 별도 side 컬럼을 두지 않아
    // "side와 부호가 서로 다른 값을 가리키는" 상태 자체가 존재할 수 없다.
    @Column(name = "threshold_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal thresholdPercent;

    @Column(nullable = false, length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_label", length = 20)
    private ColorLabel colorLabel;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MarketMapScaleThreshold() {}

    public static MarketMapScaleThreshold create(BigDecimal thresholdPercent, String color, ColorLabel colorLabel) {
        var entity = new MarketMapScaleThreshold();
        entity.thresholdPercent = thresholdPercent;
        entity.color = color;
        entity.colorLabel = colorLabel;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(BigDecimal thresholdPercent, String color, ColorLabel colorLabel) {
        this.thresholdPercent = thresholdPercent;
        this.color = color;
        this.colorLabel = colorLabel;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
