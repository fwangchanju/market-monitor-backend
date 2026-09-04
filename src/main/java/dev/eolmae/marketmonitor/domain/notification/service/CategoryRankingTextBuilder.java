package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketValueTierItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateMarketRanking;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 카테고리 등락률 TOP3 랭킹 텍스트 생성 — 섹터 캡션(MarketMapCategoryRankingTelegramReportSender)과 맵
// 캡션(MarketMapTelegramReportSender)이 둘 다 재사용한다. sender끼리는 서로 같은 레벨의 객체라 하나가
// 다른 하나를 주입받는 건 부자연스러워서, 공통으로 의존할 별도 컴포넌트로 뺐다.
@Component
@RequiredArgsConstructor
public class CategoryRankingTextBuilder {

    private static final int BEFORE_MINUTES = 60;
    private static final int TOP_N = 3;

    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketValueTierThresholdService marketValueTierThresholdService;

    /** query가 담고 있는 마켓 각각의 TOP3 카테고리 랭킹을 텍스트로 묶어 만든다("KOSPI\n카테고리: +x.xx%\n\n
     * KOSDAQ\n.." 형태) — 단일 마켓 쿼리면 그 마켓 하나만 있는 텍스트가 된다. 데이터 없는 마켓은
     * findRankingForMarkets가 이미 결과에서 뺀 상태라 자동으로 텍스트에서도 빠진다. 헤더("Custom Sector"
     * 등)는 안 붙이므로 호출부가 자기 맥락에 맞는 헤더를 붙여 쓴다. */
    public String buildRankingText(LocalDateTime dataTime, MarketQuery query) {
        List<Market> markets = query.toMarkets();
        SnapshotResponse<CategoryChangeRateMarketRanking> ranking =
                marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(markets, dataTime, BEFORE_MINUTES);

        // 화면(CategoryChangeRatePage)도 대분류(부모 없는 카테고리)만 골라 랭킹을 매기므로, 텍스트도 같은
        // 기준으로 맞춘다 — findRankingForMarkets 자체는 전체 뎁스를 다 돌려주므로 여기서 걸러낸다.
        Map<Long, String> categoryNameById = new HashMap<>();
        Set<Long> rootCategoryIds = new HashSet<>();
        for (MarketMapCategory category : marketMapCategoryRepository.findAll()) {
            categoryNameById.put(category.getId(), category.getName());
            if (category.hasNoParent()) {
                rootCategoryIds.add(category.getId());
            }
        }

        // 함께 발송되는 이미지(렌더러가 캡처하는 화면)가 기본 제외 구간을 걸러내고 그리므로, 텍스트도 같은
        // 기준(market_value_tier_threshold.is_excluded_by_default)으로 맞춘다.
        Set<Long> excludedTierIds = marketValueTierThresholdService.getValueTiers().stream()
                .filter(MarketValueTierItem::isExcludedByDefault)
                .map(MarketValueTierItem::id)
                .collect(Collectors.toSet());

        return ranking.items().stream()
                .map(marketRanking -> {
                    List<RankedCategory> top = marketRanking.items().stream()
                            .filter(item -> rootCategoryIds.contains(item.categoryId()))
                            .map(item -> {
                                List<CategoryTierBreakdown> included = item.now().stream()
                                        .filter(breakdown -> !excludedTierIds.contains(breakdown.tierId()))
                                        .toList();
                                return new RankedCategory(
                                        item.categoryId(), marketMapCategoryChangeRateSnapshotService.combine(included));
                            })
                            .sorted(Comparator.comparing((RankedCategory ranked) -> ranked.now().weightedAvgChangeRate())
                                    .reversed())
                            .limit(TOP_N)
                            .toList();

                    String rankingLines = top.stream()
                            .map(ranked -> {
                                String categoryName = categoryNameById.getOrDefault(ranked.categoryId(), "");
                                return categoryName + ": " + formatPercent(ranked.now().weightedAvgChangeRate());
                            })
                            .collect(Collectors.joining("\n"));

                    return marketRanking.market() + "\n" + rankingLines;
                })
                .collect(Collectors.joining("\n\n"));
    }

    private record RankedCategory(Long categoryId, SnapshotAverages now) {}

    private String formatPercent(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        String sign = rounded.signum() > 0 ? "+" : "";
        return sign + rounded.toPlainString() + "%";
    }
}
