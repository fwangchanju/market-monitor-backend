package dev.eolmae.marketmonitor.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketValueTierItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateMarketRanking;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class CategoryRankingTextBuilderTest {

    // combine()이 필드를 전혀 참조하지 않는 순수 계산이라, 진짜 구현을 그대로 쓰고
    // findRankingForMarkets()만 스텁한다.
    private final MarketMapCategoryChangeRateSnapshotService snapshotService =
            Mockito.mock(MarketMapCategoryChangeRateSnapshotService.class, Mockito.CALLS_REAL_METHODS);
    private final MarketMapCategoryRepository marketMapCategoryRepository =
            Mockito.mock(MarketMapCategoryRepository.class);
    private final MarketValueTierThresholdService marketValueTierThresholdService =
            Mockito.mock(MarketValueTierThresholdService.class);
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository =
            Mockito.mock(MarketOverviewSnapshotRepository.class);
    private final CategoryRankingTextBuilder builder = new CategoryRankingTextBuilder(
            snapshotService,
            marketMapCategoryRepository,
            marketValueTierThresholdService,
            marketOverviewSnapshotRepository);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 15, 0);

    @Test
    void buildRankingText_자식_카테고리는_랭킹에서_제외된다() {
        MarketMapCategory root = category(1L, null, "반도체");
        MarketMapCategory child = category(2L, 1L, "반도체 소재");

        // child가 root보다 등락률이 훨씬 높아도, 대분류가 아니므로 결과에 나오면 안 된다.
        stub(
                List.of(root, child),
                List.of(),
                item(root.getId(), tier(10L, "대형", 50_000, 10000)), // +5%
                item(child.getId(), tier(10L, "대형", 900_000, 10000))); // +90%

        String text = builder.buildRankingText(dataTime, MarketQuery.KOSPI);

        assertThat(text).isEqualTo("#코스피\n반도체 +5.00%");
    }

    @Test
    void buildRankingText_TOP3까지만_등락률_내림차순으로_노출된다() {
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        MarketMapCategory c = category(3L, null, "자동차");
        MarketMapCategory d = category(4L, null, "철강");

        stub(
                List.of(a, b, c, d),
                List.of(),
                item(a.getId(), tier(10L, "대형", 100_000, 10000)), // +10%
                item(b.getId(), tier(10L, "대형", 50_000, 10000)), // +5%
                item(c.getId(), tier(10L, "대형", 20_000, 10000)), // +2%
                item(d.getId(), tier(10L, "대형", 10_000, 10000))); // +1%, 4위라 빠져야 함

        String text = builder.buildRankingText(dataTime, MarketQuery.KOSPI);

        assertThat(text).isEqualTo("#코스피\n반도체 +10.00%\n화학 +5.00%\n자동차 +2.00%");
    }

    @Test
    void buildRankingText_기본_제외_구간은_평균_계산에서_빠진다() {
        MarketMapCategory root = category(1L, null, "반도체");

        // tier(20L)이 결과에 포함되면 -20%, 빠지면 +10% — 제외가 실제로 적용됐는지 값으로 검증한다.
        stub(
                List.of(root),
                List.of(20L),
                item(
                        root.getId(),
                        tier(10L, "대형", 100_000, 10000), // +10%, 포함
                        tier(20L, "소형", -500_000, 10000))); // -50%, 제외 대상

        String text = builder.buildRankingText(dataTime, MarketQuery.KOSPI);

        assertThat(text).isEqualTo("#코스피\n반도체 +10.00%");
    }

    @Test
    void buildRankingText_음수_등락률은_부호없이_마이너스로만_붙는다() {
        MarketMapCategory root = category(1L, null, "반도체");

        stub(List.of(root), List.of(), item(root.getId(), tier(10L, "대형", -123_400, 10000))); // -12.34%

        String text = builder.buildRankingText(dataTime, MarketQuery.KOSPI);

        assertThat(text).isEqualTo("#코스피\n반도체 -12.34%");
    }

    @Test
    void buildRankingText_여러_마켓은_빈줄로_구분되어_이어붙는다() {
        MarketMapCategory kospiCategory = category(1L, null, "반도체");
        MarketMapCategory kosdaqCategory = category(2L, null, "제약");

        SnapshotResponse<CategoryChangeRateMarketRanking> response = new SnapshotResponse<>(
                dataTime,
                List.of(
                        new CategoryChangeRateMarketRanking(
                                Market.KOSPI,
                                List.of(CategoryChangeRateItem.withoutBefore(
                                        kospiCategory.getId(), List.of(tier(10L, "대형", 50_000, 10000))))),
                        new CategoryChangeRateMarketRanking(
                                Market.KOSDAQ,
                                List.of(CategoryChangeRateItem.withoutBefore(
                                        kosdaqCategory.getId(), List.of(tier(10L, "대형", 30_000, 10000)))))));
        doReturn(response).when(snapshotService).findRankingForMarkets(any(), any(), anyInt());
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(kospiCategory, kosdaqCategory));
        when(marketValueTierThresholdService.getValueTiers()).thenReturn(List.of());
        when(marketOverviewSnapshotRepository.findBySnapshotTime(dataTime)).thenReturn(List.of());

        String text = builder.buildRankingText(dataTime, MarketQuery.ALL_STOCK);

        assertThat(text).isEqualTo("#코스피\n반도체 +5.00%\n\n#코스닥\n제약 +3.00%");
    }

    @Test
    void buildRankingText_헤더_옆에_마켓_지수_변화율이_붙는다() {
        MarketMapCategory root = category(1L, null, "반도체");
        stub(List.of(root), List.of(), item(root.getId(), tier(10L, "대형", 50_000, 10000))); // +5%
        when(marketOverviewSnapshotRepository.findBySnapshotTime(dataTime))
                .thenReturn(List.of(marketOverview(Market.KOSPI, BigDecimal.valueOf(-1.23))));

        String text = builder.buildRankingText(dataTime, MarketQuery.KOSPI);

        assertThat(text).isEqualTo("#코스피 -1.23%\n반도체 +5.00%");
    }

    private void stub(List<MarketMapCategory> categories, List<Long> excludedTierIds, CategoryChangeRateItem... items) {
        SnapshotResponse<CategoryChangeRateMarketRanking> response = new SnapshotResponse<>(
                dataTime, List.of(new CategoryChangeRateMarketRanking(Market.KOSPI, List.of(items))));
        doReturn(response).when(snapshotService).findRankingForMarkets(any(), any(), anyInt());
        when(marketMapCategoryRepository.findAll()).thenReturn(categories);
        when(marketValueTierThresholdService.getValueTiers())
                .thenReturn(excludedTierIds.stream()
                        .map(id -> new MarketValueTierItem(id, "제외구간", 0L, true))
                        .toList());
        when(marketOverviewSnapshotRepository.findBySnapshotTime(dataTime)).thenReturn(List.of());
    }

    private MarketOverviewSnapshot marketOverview(Market market, BigDecimal changeRate) {
        return MarketOverviewSnapshot.create(
                market,
                dataTime,
                BigDecimal.valueOf(2500),
                BigDecimal.ONE,
                changeRate,
                BigDecimal.ZERO,
                "OPEN",
                0,
                0,
                0,
                0,
                0,
                dataTime);
    }

    private CategoryChangeRateItem item(Long categoryId, CategoryTierBreakdown... breakdowns) {
        return CategoryChangeRateItem.withoutBefore(categoryId, List.of(breakdowns));
    }

    private CategoryTierBreakdown tier(Long tierId, String label, long weightedSum, long totalValue) {
        return new CategoryTierBreakdown(
                tierId,
                label,
                BigDecimal.valueOf(weightedSum),
                BigDecimal.valueOf(totalValue),
                BigDecimal.valueOf(weightedSum),
                1);
    }

    private MarketMapCategory category(Long id, Long parentId, String name) {
        MarketMapCategory category =
                parentId == null ? MarketMapCategory.createParent(name) : categoryWithParent(parentId, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private MarketMapCategory categoryWithParent(Long parentId, String name) {
        MarketMapCategory parent = MarketMapCategory.createParent("parent-placeholder");
        ReflectionTestUtils.setField(parent, "id", parentId);
        return MarketMapCategory.createChild(name, parent);
    }
}
