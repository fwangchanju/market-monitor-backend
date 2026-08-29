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

// 카테고리(하위 재귀 포함) × 시가총액 구간별 등락률 원시 합계. 이미 나눠진 평균이 아니라 분자/분모를
// 그대로 저장 — 여러 구간을 조합해 가중/산술평균을 낼 때(예: 중형주 제외) 나눠진 값끼리 다시 평균내면
// 틀리기 때문에(구간별 종목 수/시총 비중을 모르면 정확히 재구성 불가), 조회 시점에 필요한 구간들의
// 분자/분모를 합산한 뒤 마지막에 한 번만 나눈다.
@Table(
        name = "market_map_category_change_rate_snapshot",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_market_map_category_change_rate_snapshot",
                        columnNames = {"market_type", "category_id", "market_value_tier_id", "snapshot_time"}))
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

    @Column(name = "market_value_tier_id", nullable = false)
    private Long marketValueTierId;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    // Σ(등락률 × 시총) — 가중평균의 분자.
    @Column(name = "weighted_sum", nullable = false, precision = 30, scale = 4)
    private BigDecimal weightedSum;

    // Σ(시총) — 가중평균의 분모.
    @Column(name = "total_value", nullable = false, precision = 30, scale = 4)
    private BigDecimal totalValue;

    // Σ(등락률) — 산술평균의 분자.
    @Column(name = "simple_sum", nullable = false, precision = 19, scale = 4)
    private BigDecimal simpleSum;

    // 산술평균의 분모(종목 수).
    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected MarketMapCategoryChangeRateSnapshot() {}

    public static MarketMapCategoryChangeRateSnapshot create(
            Market marketType,
            Long categoryId,
            Long marketValueTierId,
            LocalDateTime snapshotTime,
            BigDecimal weightedSum,
            BigDecimal totalValue,
            BigDecimal simpleSum,
            Integer itemCount) {
        var entity = new MarketMapCategoryChangeRateSnapshot();
        entity.marketType = marketType;
        entity.categoryId = categoryId;
        entity.marketValueTierId = marketValueTierId;
        entity.snapshotTime = snapshotTime;
        entity.weightedSum = weightedSum;
        entity.totalValue = totalValue;
        entity.simpleSum = simpleSum;
        entity.itemCount = itemCount;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
