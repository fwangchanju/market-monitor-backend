package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketValueTierItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class MarketMapCategoryRankingTelegramReportSender extends TelegramReportSender {

    private static final int BEFORE_MINUTES = 60;
    private static final int TOP_N = 3;

    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketValueTierThresholdService marketValueTierThresholdService;

    public MarketMapCategoryRankingTelegramReportSender(
            MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService,
            MarketMapCategoryRepository marketMapCategoryRepository,
            MarketValueTierThresholdService marketValueTierThresholdService,
            ScreenshotClient screenshotClient,
            TelegramClient telegramClient,
            TelegramProperties telegramProperties) {
        super(screenshotClient, telegramClient, telegramProperties);
        this.marketMapCategoryChangeRateSnapshotService = marketMapCategoryChangeRateSnapshotService;
        this.marketMapCategoryRepository = marketMapCategoryRepository;
        this.marketValueTierThresholdService = marketValueTierThresholdService;
    }

    @Override
    protected RenderTarget target() {
        return RenderTarget.CATEGORY_CHANGE_RATE;
    }

    @Override
    protected String buildText(LocalDateTime dataTime, Market market) {
        SnapshotResponse<CategoryChangeRateItem> ranking =
                marketMapCategoryChangeRateSnapshotService.findRanking(market, dataTime, BEFORE_MINUTES);

        Map<Long, String> categoryNameById = marketMapCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MarketMapCategory::getId, MarketMapCategory::getName));

        // 함께 발송되는 이미지(렌더러가 캡처하는 화면)가 기본 제외 구간을 걸러내고 그리므로, 캡션도 같은
        // 기준(market_value_tier_threshold.is_excluded_by_default)으로 맞춘다.
        Set<Long> excludedTierIds = marketValueTierThresholdService.getValueTiers().stream()
                .filter(MarketValueTierItem::isExcludedByDefault)
                .map(MarketValueTierItem::id)
                .collect(Collectors.toSet());

        List<RankedCategory> top = ranking.items().stream()
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

        StringBuilder text = new StringBuilder(market + " Custom Sector\n")
                .append(ranking.snapshotTime().format(DateTimePattern.DATETIME_MINUTE_WITH_WEEKDAY.formatter()))
                .append("\n");

        String rankingLines = IntStream.range(0, top.size())
                .mapToObj(i -> {
                    RankedCategory ranked = top.get(i);
                    String categoryName = categoryNameById.getOrDefault(ranked.categoryId(), "");
                    return (i + 1) + ". " + categoryName + ": " + formatPercent(ranked.now().weightedAvgChangeRate());
                })
                .collect(Collectors.joining("\n"));
        return text.append(rankingLines).toString();
    }

    private record RankedCategory(Long categoryId, SnapshotAverages now) {}

    private String formatPercent(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        String sign = rounded.signum() > 0 ? "+" : "";
        return sign + rounded.toPlainString() + "%";
    }
}
