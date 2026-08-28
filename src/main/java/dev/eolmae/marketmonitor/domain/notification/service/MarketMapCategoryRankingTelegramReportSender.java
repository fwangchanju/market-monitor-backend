package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.enums.RenderTarget;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClient;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class MarketMapCategoryRankingTelegramReportSender extends TelegramReportSender {

    private static final Market MARKET = Market.KOSPI;
    private static final int BEFORE_MINUTES = 60;
    private static final int TOP_N = 3;

    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;
    private final MarketMapCategoryRepository marketMapCategoryRepository;

    public MarketMapCategoryRankingTelegramReportSender(
            MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService,
            MarketMapCategoryRepository marketMapCategoryRepository,
            ScreenshotClient screenshotClient,
            TelegramClient telegramClient,
            TelegramProperties telegramProperties) {
        super(screenshotClient, telegramClient, telegramProperties);
        this.marketMapCategoryChangeRateSnapshotService = marketMapCategoryChangeRateSnapshotService;
        this.marketMapCategoryRepository = marketMapCategoryRepository;
    }

    @Override
    protected RenderTarget target() {
        return RenderTarget.CATEGORY_CHANGE_RATE;
    }

    @Override
    protected String buildText(LocalDateTime dataTime) {
        SnapshotResponse<CategoryChangeRateItem> ranking =
                marketMapCategoryChangeRateSnapshotService.findRanking(MARKET, dataTime, BEFORE_MINUTES);

        Map<Long, String> categoryNameById = marketMapCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(MarketMapCategory::getId, MarketMapCategory::getName));

        List<CategoryChangeRateItem> top = ranking.items().stream()
                .sorted(Comparator.comparing(
                                (CategoryChangeRateItem item) -> item.now().weightedAvgChangeRate())
                        .reversed())
                .limit(TOP_N)
                .toList();

        StringBuilder text = new StringBuilder("CUSTOM KOSPI INDUSTRY\n")
                .append(ranking.snapshotTime().format(DateTimePattern.DATETIME_MINUTE_WITH_WEEKDAY.formatter()))
                .append(" (5분 간격)\n");

        String rankingLines = IntStream.range(0, top.size())
                .mapToObj(i -> {
                    CategoryChangeRateItem item = top.get(i);
                    String categoryName = categoryNameById.getOrDefault(item.categoryId(), "");
                    return (i + 1) + ". " + categoryName + ": " + formatPercent(item.now().weightedAvgChangeRate());
                })
                .collect(Collectors.joining("\n"));
        return text.append(rankingLines).toString();
    }

    private String formatPercent(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        String sign = rounded.signum() > 0 ? "+" : "";
        return sign + rounded.toPlainString() + "%";
    }
}
