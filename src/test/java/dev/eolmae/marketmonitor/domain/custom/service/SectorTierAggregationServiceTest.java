package dev.eolmae.marketmonitor.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SectorTierAggregationServiceTest {

    private final CustomValueTierThresholdRepository marketValueTierThresholdRepository =
            mock(CustomValueTierThresholdRepository.class);
    private final SectorTierAggregationService service =
            new SectorTierAggregationService(marketValueTierThresholdRepository);

    @Test
    void aggregateByCategory_하위_카테고리_항목이_부모_합계에_재귀로_포함된다() {
        CustomValueTierThreshold large = tier(10L, "대형");
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(999999L))
                .thenReturn(List.of(large));

        MarketMapCategoryNode child = leaf(2L, "반도체 소재", List.of(item("000660", "대형", BigDecimal.TEN, 10_000)));
        MarketMapCategoryNode parent = new MarketMapCategoryNode(
                1L,
                "반도체",
                false,
                BigDecimal.ZERO,
                List.of(child),
                List.of(item("005930", "대형", BigDecimal.TEN, 10_000)));

        Map<Long, List<CategoryTierBreakdown>> result = service.aggregateByCategory(List.of(parent));

        // 부모(1L)는 자기 items(005930) + 자식(000660)까지 재귀로 합친 값을 가진다.
        CategoryTierBreakdown parentBreakdown = result.get(1L).get(0);
        assertThat(parentBreakdown.totalValue()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(parentBreakdown.itemCount()).isEqualTo(2);
        // 자식(2L)은 자기 items만 가진다.
        CategoryTierBreakdown childBreakdown = result.get(2L).get(0);
        assertThat(childBreakdown.totalValue()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
        assertThat(childBreakdown.itemCount()).isEqualTo(1);
    }

    @Test
    void aggregateByCategory_시가총액_구간별로_따로_묶인다() {
        CustomValueTierThreshold large = tier(10L, "대형");
        CustomValueTierThreshold small = tier(20L, "소형");
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(999999L))
                .thenReturn(List.of(large, small));

        MarketMapCategoryNode node = leaf(
                1L,
                "반도체",
                List.of(
                        item("005930", "대형", BigDecimal.TEN, 10_000),
                        item("000660", "소형", BigDecimal.valueOf(-5), 1_000)));

        Map<Long, List<CategoryTierBreakdown>> result = service.aggregateByCategory(List.of(node));

        assertThat(result.get(1L)).extracting(CategoryTierBreakdown::tierLabel).containsExactlyInAnyOrder("대형", "소형");
        CategoryTierBreakdown largeBreakdown = result.get(1L).stream()
                .filter(b -> b.tierId().equals(10L))
                .findFirst()
                .orElseThrow();
        assertThat(largeBreakdown.itemCount()).isEqualTo(1);
    }

    @Test
    void aggregateByCategory_가중합과_산술합을_각각_구한다() {
        CustomValueTierThreshold large = tier(10L, "대형");
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(999999L))
                .thenReturn(List.of(large));

        // 시총 90,000짜리 +30%, 시총 10,000짜리 +10% — 가중합은 시총에 끌리고 산술합은 종목당 등락률 합.
        MarketMapCategoryNode node = leaf(
                1L,
                "반도체",
                List.of(
                        item("005930", "대형", BigDecimal.valueOf(30), 90_000),
                        item("000660", "대형", BigDecimal.valueOf(10), 10_000)));

        Map<Long, List<CategoryTierBreakdown>> result = service.aggregateByCategory(List.of(node));

        CategoryTierBreakdown breakdown = result.get(1L).get(0);
        // weightedSum = 30*90,000 + 10*10,000 = 2,800,000, totalValue = 100,000
        assertThat(breakdown.weightedSum()).isEqualByComparingTo(BigDecimal.valueOf(2_800_000));
        assertThat(breakdown.totalValue()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        // simpleSum = 30 + 10 = 40, itemCount = 2
        assertThat(breakdown.simpleSum()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(breakdown.itemCount()).isEqualTo(2);
    }

    @Test
    void aggregateByCategory_종목이_없는_카테고리는_결과_맵에_없다() {
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(999999L))
                .thenReturn(List.of());

        MarketMapCategoryNode empty = leaf(1L, "빈카테고리", List.of());

        Map<Long, List<CategoryTierBreakdown>> result = service.aggregateByCategory(List.of(empty));

        assertThat(result).doesNotContainKey(1L);
    }

    @Test
    void combine_원시값을_합산한_뒤_한_번만_나눈다() {
        // 시총 10,000에 +10%p, 시총 40,000에 -2%p — 원시값을 합산한 뒤 나누면 +0.4가 맞다.
        CategoryTierBreakdown tierA = new CategoryTierBreakdown(
                10L, "대형", BigDecimal.valueOf(100_000), BigDecimal.valueOf(10_000), BigDecimal.TEN, 1);
        CategoryTierBreakdown tierB = new CategoryTierBreakdown(
                10L, "대형", BigDecimal.valueOf(-80_000), BigDecimal.valueOf(40_000), BigDecimal.valueOf(-2), 1);

        SnapshotAverages averages = service.combine(List.of(tierA, tierB));

        assertThat(averages.weightedAvgChangeRate()).isEqualByComparingTo(BigDecimal.valueOf(0.4));
        assertThat(averages.simpleAvgChangeRate()).isEqualByComparingTo(BigDecimal.valueOf(4));
    }

    @Test
    void combine_빈_목록이면_0을_반환한다() {
        SnapshotAverages averages = service.combine(List.of());

        assertThat(averages.weightedAvgChangeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(averages.simpleAvgChangeRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private CustomValueTierThreshold tier(Long id, String label) {
        CustomValueTierThreshold threshold = CustomValueTierThreshold.create(label, 0L, false);
        ReflectionTestUtils.setField(threshold, "id", id);
        return threshold;
    }

    private MarketMapCategoryNode leaf(Long categoryId, String categoryName, List<MarketMapItem> items) {
        return new MarketMapCategoryNode(categoryId, categoryName, false, BigDecimal.ZERO, List.of(), items);
    }

    private MarketMapItem item(String stockCode, String tierLabel, BigDecimal changeRate, long totalMarketValue) {
        return new MarketMapItem(
                stockCode,
                stockCode,
                null,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.valueOf(totalMarketValue),
                tierLabel,
                changeRate,
                LocalDateTime.of(2026, 7, 31, 10, 0));
    }
}
