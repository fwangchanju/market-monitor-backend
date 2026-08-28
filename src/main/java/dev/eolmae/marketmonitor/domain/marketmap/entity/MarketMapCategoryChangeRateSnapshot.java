package dev.eolmae.marketmonitor.domain.marketmap.entity;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(
        name = "market_map_category_change_rate_snapshot",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_market_map_category_change_rate_snapshot",
                        columnNames = {"market_type", "category_id", "snapshot_time"}))
@Entity
@Getter
public class MarketMapCategoryChangeRateSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Market marketType;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    // 시가총액 가중평균 — 랭킹/바 차트가 실제로 쓰는 값.
    @Column(name = "weighted_avg_change_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal weightedAvgChangeRate;

    // 종목 수 기준 단순 산술평균 — 참고/비교용.
    @Column(name = "simple_avg_change_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal simpleAvgChangeRate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected MarketMapCategoryChangeRateSnapshot() {}

    public static MarketMapCategoryChangeRateSnapshot create(
            Market marketType,
            Long categoryId,
            LocalDateTime snapshotTime,
            BigDecimal weightedAvgChangeRate,
            BigDecimal simpleAvgChangeRate) {
        var entity = new MarketMapCategoryChangeRateSnapshot();
        entity.marketType = marketType;
        entity.categoryId = categoryId;
        entity.snapshotTime = snapshotTime;
        entity.weightedAvgChangeRate = weightedAvgChangeRate;
        entity.simpleAvgChangeRate = simpleAvgChangeRate;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
