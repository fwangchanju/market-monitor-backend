package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketValueTierItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketValueTierThresholdRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시가총액 구간(초대형주/대형주/...) 분류 — 코드 enum이 아니라 market_value_tier_threshold 데이터 기준. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketValueTierThresholdService {

    private final MarketValueTierThresholdRepository marketValueTierThresholdRepository;

    public List<MarketValueTierThreshold> findAllSortedAscending() {
        return marketValueTierThresholdRepository.findAllByOrderByThresholdValueAsc();
    }

    /** 프론트 필터 UI(시가총액 구간 슬라이더)가 하드코딩 대신 조회해서 그리는 용도. */
    public List<MarketValueTierItem> getValueTiers() {
        return findAllSortedAscending().stream()
                .map(tier -> new MarketValueTierItem(
                        tier.getId(), tier.getLabel(), tier.getThresholdValue(), tier.isExcludedByDefault()))
                .toList();
    }

    /** sortedTier 기준으로 totalMarketValue가 속하는 가장 높은 구간의 label. 가장 낮은 구간의
     * threshold보다도 작으면 가장 낮은 구간으로 분류한다(미분류로 남기지 않음). */
    public String resolveTier(List<MarketValueTierThreshold> sortedTier, BigDecimal totalMarketValue) {
        String label = sortedTier.getFirst().getLabel();
        for (MarketValueTierThreshold threshold : sortedTier) {
            if (threshold.isNotReachedBy(totalMarketValue)) {
                break;
            }
            label = threshold.getLabel();
        }
        return label;
    }
}
