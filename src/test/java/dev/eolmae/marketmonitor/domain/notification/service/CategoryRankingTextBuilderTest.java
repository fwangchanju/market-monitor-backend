package dev.eolmae.marketmonitor.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryRankingSummary;
import dev.eolmae.marketmonitor.domain.view.dto.TopCategoryItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

// TOP3 선정, 대분류 필터, 기본 제외 구간 제외는 MarketMapQueryService가 랭킹을 확정할 때 끝낸다.
// 여기서는 확정된 CategoryRankingSummary를 텍스트로 조립하는 포맷팅만 검증한다.
class CategoryRankingTextBuilderTest {

    private final CategoryRankingTextBuilder builder = new CategoryRankingTextBuilder();

    @Test
    void buildRankingText_카테고리들을_받은_순서_그대로_이어붙인다() {
        CategoryRankingSummary summary = new CategoryRankingSummary(
                Market.KOSPI,
                null,
                List.of(
                        new TopCategoryItem("반도체", BigDecimal.valueOf(10)),
                        new TopCategoryItem("화학", BigDecimal.valueOf(5)),
                        new TopCategoryItem("자동차", BigDecimal.valueOf(2))));

        String text = builder.buildRankingText(List.of(summary));

        assertThat(text).isEqualTo("#코스피\n반도체 +10.00%p\n화학 +5.00%p\n자동차 +2.00%p");
    }

    @Test
    void buildRankingText_지수_등락률이_있으면_헤더_옆에_붙는다() {
        CategoryRankingSummary summary = new CategoryRankingSummary(
                Market.KOSPI, BigDecimal.valueOf(-1.23), List.of(new TopCategoryItem("반도체", BigDecimal.valueOf(5))));

        String text = builder.buildRankingText(List.of(summary));

        assertThat(text).isEqualTo("#코스피 -1.23%\n반도체 +5.00%p");
    }

    @Test
    void buildRankingText_지수_등락률이_없으면_헤더에_퍼센트를_붙이지_않는다() {
        CategoryRankingSummary summary = new CategoryRankingSummary(
                Market.KOSPI, null, List.of(new TopCategoryItem("반도체", BigDecimal.valueOf(5))));

        String text = builder.buildRankingText(List.of(summary));

        assertThat(text).isEqualTo("#코스피\n반도체 +5.00%p");
    }

    @Test
    void buildRankingText_음수_등락률은_부호없이_마이너스로만_붙는다() {
        CategoryRankingSummary summary = new CategoryRankingSummary(
                Market.KOSPI, null, List.of(new TopCategoryItem("반도체", BigDecimal.valueOf(-12.34))));

        String text = builder.buildRankingText(List.of(summary));

        assertThat(text).isEqualTo("#코스피\n반도체 -12.34%p");
    }

    @Test
    void buildRankingText_여러_마켓은_빈줄로_구분되어_이어붙는다() {
        CategoryRankingSummary kospi = new CategoryRankingSummary(
                Market.KOSPI, null, List.of(new TopCategoryItem("반도체", BigDecimal.valueOf(5))));
        CategoryRankingSummary kosdaq = new CategoryRankingSummary(
                Market.KOSDAQ, null, List.of(new TopCategoryItem("제약", BigDecimal.valueOf(3))));

        String text = builder.buildRankingText(List.of(kospi, kosdaq));

        assertThat(text).isEqualTo("#코스피\n반도체 +5.00%p\n\n#코스닥\n제약 +3.00%p");
    }

    @Test
    void buildSectorCaption_헤더에_대괄호와_N분_전_대비_라벨을_붙인다() {
        CategoryRankingSummary summary = new CategoryRankingSummary(
                Market.KOSPI,
                BigDecimal.valueOf(-1.23),
                List.of(
                        new TopCategoryItem("제약", BigDecimal.valueOf(2.1)),
                        new TopCategoryItem("운송", BigDecimal.valueOf(1.75))));

        String text = builder.buildSectorCaption(summary, 15);

        assertThat(text).isEqualTo("[#코스피 15분 전 대비]\n제약 +2.10%p\n운송 +1.75%p");
    }

    @Test
    void buildSectorCaption_지수_등락률이_있어도_헤더에는_안_붙인다() {
        CategoryRankingSummary summary = new CategoryRankingSummary(
                Market.KOSDAQ, BigDecimal.valueOf(3.0), List.of(new TopCategoryItem("반도체", BigDecimal.valueOf(1))));

        String text = builder.buildSectorCaption(summary, 30);

        assertThat(text).isEqualTo("[#코스닥 30분 전 대비]\n반도체 +1.00%p");
    }

    @Test
    void buildSectorFallbackCaption_헤더가_등락률_라벨이고_단위는_퍼센트다() {
        CategoryRankingSummary summary = new CategoryRankingSummary(
                Market.KOSPI,
                null,
                List.of(
                        new TopCategoryItem("반도체", BigDecimal.valueOf(2.1)),
                        new TopCategoryItem("화학", BigDecimal.valueOf(-1.5))));

        String text = builder.buildSectorFallbackCaption(summary);

        assertThat(text).isEqualTo("[#코스피 섹터 등락률]\n반도체 +2.10%\n화학 -1.50%");
    }

    @Test
    void buildMapCaption_마켓_구분_없이_두_줄로_이어붙인다() {
        List<TopCategoryItem> topCategories = List.of(
                new TopCategoryItem("반도체", BigDecimal.valueOf(1.35)),
                new TopCategoryItem("제약", BigDecimal.valueOf(1.20)));

        String text = builder.buildMapCaption(topCategories);

        assertThat(text).isEqualTo("[#코스피 / #코스닥 섹터 등락률]\n반도체 +1.35%\n제약 +1.20%");
    }
}
