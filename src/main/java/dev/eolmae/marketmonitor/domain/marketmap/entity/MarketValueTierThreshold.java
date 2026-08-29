package dev.eolmae.marketmonitor.domain.marketmap.entity;

import dev.eolmae.marketmonitor.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

// 초대형주/대형주/중형주/소형주 같은 시가총액 구간 정의 — 코드 enum이 아니라 데이터로 관리한다.
// 지금은 4행 고정이지만, 나중에 userId가 붙으면 사용자마다 구간 개수·이름·기준값이 달라질 수 있어서
// 애초에 고정 개수를 전제하는 enum으로는 표현이 불가능하다. label은 영문 코드/한글 표시명을 나누지 않고
// 하나만 둔다(구분할 이유가 없음).
@Table(name = "market_value_tier_threshold")
@Entity
@Getter
public class MarketValueTierThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "threshold_value", nullable = false)
    private Long thresholdValue;

    @Column(name = "is_excluded_by_default", nullable = false)
    private boolean isExcludedByDefault;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MarketValueTierThreshold() {}

    public static MarketValueTierThreshold create(String label, Long thresholdValue, boolean isExcludedByDefault) {
        var entity = new MarketValueTierThreshold();
        entity.label = label;
        entity.thresholdValue = thresholdValue;
        entity.isExcludedByDefault = isExcludedByDefault;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public boolean isReachedBy(BigDecimal totalMarketValue) {
        return totalMarketValue.compareTo(BigDecimal.valueOf(thresholdValue)) >= 0;
    }

    public boolean isNotReachedBy(BigDecimal totalMarketValue) {
        return !isReachedBy(totalMarketValue);
    }
}
