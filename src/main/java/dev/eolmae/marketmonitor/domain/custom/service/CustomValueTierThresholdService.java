package dev.eolmae.marketmonitor.domain.custom.service;

import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
import dev.eolmae.marketmonitor.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomValueTierThresholdRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시가총액 구간(초대형주/대형주/...) 분류 — 코드 enum이 아니라 custom_value_tier_threshold 데이터 기준. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomValueTierThresholdService {

    private final CustomValueTierThresholdRepository customValueTierThresholdRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<CustomValueTierThreshold> findAllSortedAscending() {
        Long userId = CurrentUser.currentId();
        if (userId != null) {
            return findAllSortedAscending(userId);
        }
        return findDefaultSortedAscending();
    }

    public List<CustomValueTierThreshold> findAllSortedAscending(Long userId) {
        return customValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(userId);
    }

    public List<CustomValueTierThreshold> findDefaultSortedAscending() {
        return jdbcTemplate.query(
                "SELECT label, threshold_value, is_excluded_by_default FROM default_value_tier_threshold ORDER BY threshold_value, label",
                (resultSet, rowNumber) -> CustomValueTierThreshold.createDefault(
                        resultSet.getLong("threshold_value"),
                        resultSet.getString("label"),
                        resultSet.getLong("threshold_value"),
                        resultSet.getBoolean("is_excluded_by_default")));
    }

    /** 프론트 필터 UI(시가총액 구간 슬라이더)가 하드코딩 대신 조회해서 그리는 용도. */
    public List<CustomValueTierItem> getValueTiers() {
        return findAllSortedAscending().stream()
                .map(tier -> new CustomValueTierItem(
                        tier.getId(), tier.getLabel(), tier.getThresholdValue(), tier.isExcludedByDefault()))
                .toList();
    }

    public List<CustomValueTierItem> getDefaultValueTiers() {
        return findDefaultSortedAscending().stream()
                .map(tier -> new CustomValueTierItem(
                        tier.getId(), tier.getLabel(), tier.getThresholdValue(), tier.isExcludedByDefault()))
                .toList();
    }

    public List<CustomValueTierItem> getValueTiers(Long userId) {
        return findAllSortedAscending(userId).stream()
                .map(tier -> new CustomValueTierItem(
                        tier.getId(), tier.getLabel(), tier.getThresholdValue(), tier.isExcludedByDefault()))
                .toList();
    }

    /** sortedTier 기준으로 totalMarketValue가 속하는 가장 높은 구간의 label. 가장 낮은 구간의
     * threshold보다도 작으면 가장 낮은 구간으로 분류한다(미분류로 남기지 않음). */
    public String resolveTier(List<CustomValueTierThreshold> sortedTier, BigDecimal totalMarketValue) {
        String label = sortedTier.getFirst().getLabel();
        for (CustomValueTierThreshold threshold : sortedTier) {
            if (threshold.isNotReachedBy(totalMarketValue)) {
                break;
            }
            label = threshold.getLabel();
        }
        return label;
    }
}
