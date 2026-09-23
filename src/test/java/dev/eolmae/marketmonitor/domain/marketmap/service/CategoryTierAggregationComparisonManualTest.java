package dev.eolmae.marketmonitor.domain.marketmap.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.util.KstClock;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 2절의 두 번째 확인 — 운영 DB에 저장된 옛 집계(MarketMapCategoryChangeRateSnapshotService
 * .findTierBreakdownsByCategoryId)와 새 경로(CategoryTierAggregationService.aggregateByCategory(
 * MarketMapQueryService.getCustomMarketMapTree(...)))가 같은 시각에 같은 숫자를 내는지 실데이터로
 * 대조한다. 테스트가 만든 입력만 보는 단위 테스트로는 이걸 보증할 수 없다.
 *
 * <p>이 PR까지는 수집기가 계속 저장하고 있었으므로 비교할 옛 값이 아직 DB에 있다 — PR 2에서 그 테이블이
 * 지워지면 이 테스트도 함께 지운다.
 *
 * <p>읽기만 한다. 저장·삭제를 부르지 않는다.
 *
 * <p>실행은 구현자가 하지 않는다. 운영 DB가 있는 서버에서 사용자가 돌린다. 오늘 07:00 이전 tick은
 * 빼므로(종목정보가 매일 07:00에 동기화돼 상장주식수가 바뀐 종목은 전날 tick을 오늘 종목정보로 다시
 * 계산하면 코드가 맞아도 불일치가 난다), 장중이나 장 마감 뒤 같은 날에 돌려야 한다.
 *
 * 실행 조건: 배포된 컨테이너 환경(실제 DB 설정)에서, 이 PR 브랜치를 checkout해서 실행해야 한다 —
 * 병합 전이라 아직 main에는 없다.
 * 실행 명령:
 *
 * <pre>{@code
 * cd "$HOME/repo/market-monitor-backend"
 * git fetch origin claude/refactor/remove-category-aggregate && git checkout claude/refactor/remove-category-aggregate && git pull
 * mkdir -p "$HOME/.gradle-cache"
 *
 * docker run --rm \
 *   --network host \
 *   --env-file "$HOME/env/market-monitor.env" \
 *   -e SPRING_PROFILES_ACTIVE=prod \
 *   -e DB_URL=jdbc:postgresql://localhost:5433/market_monitor_db \
 *   -v "$HOME/repo/market-monitor-backend:/workspace" \
 *   -v "$HOME/.gradle-cache:/root/.gradle" \
 *   -w /workspace \
 *   eclipse-temurin:21-jdk-jammy \
 *   bash -c "chmod +x gradlew && ./gradlew manualTest --tests '*.CategoryTierAggregationComparisonManualTest' -i --no-daemon"
 * }</pre>
 *
 * 확인할 것
 * <ol>
 *   <li>BUILD SUCCESSFUL이어야 한다 — comparedCount(비교한 (시각, 마켓) 수)가 1 이상이고
 *       mismatchCount(불일치 수)가 0이라는 뜻이다. comparedCount가 0이면(주말·공휴일에 돌린 경우,
 *       다음 날 07:00 전에 돌린 경우, 이 PR이 배포된 뒤 저장이 멈춰 옛 값이 과거 날짜에만 남은
 *       경우) assert 메시지가 그 이유를 알려준다
 *   <li>실패하면 콘솔에 찍힌 [불일치] 줄(시각·마켓·카테고리·구간·필드별 옛 값·새 값)을 PR에 그대로
 *       붙인다
 *   <li>끝의 [6절 비교 요약] 줄 — 캡처없음 수가 비정상적으로 크면(수집이 계속 실패 중이라는 뜻) 통과
 *       여부와 무관하게 먼저 그 원인을 확인한다
 *   <li>불일치가 있으면, market_map_stock_category·market_map_category·market_value_tier_threshold의
 *       updated_at으로 그 tick 이후 편집이 있었는지 확인한다 — 있으면 그 시각과 근거를 PR에 적고,
 *       설명되지 않는 불일치가 하나라도 남으면 병합하지 않는다
 * </ol>
 */
@Tag("manual")
@SpringBootTest(properties = {"scheduling.enabled=false", "spring.flyway.enabled=false"})
class CategoryTierAggregationComparisonManualTest {

    private static final List<Market> MARKETS = List.of(Market.KOSPI, Market.KOSDAQ);
    // 최근 tick 12개 — 시작점(최신 공통 시각)까지 포함해 11번 더 뒤로 간다.
    private static final int TICK_COUNT = 12;

    @Autowired
    private MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService;

    @Autowired
    private MarketMapQueryService marketMapQueryService;

    @Autowired
    private CategoryTierAggregationService categoryTierAggregationService;

    @Value("${collect.interval-minutes}")
    private int intervalMinutes;

    @Test
    void compareOldAndNewAggregation() {
        LocalDateTime latest = marketMapCategoryChangeRateSnapshotService
                .findLatestCommonSnapshotTime(MARKETS)
                .orElseThrow(() ->
                        new IllegalStateException("옛 집계 스냅샷이 없습니다 — 비교할 옛 값이 없습니다. 수집기가 아직 한 번도 안 돈 환경일 수 있습니다."));

        int comparedCount = 0;
        int matchCount = 0;
        int mismatchCount = 0;
        int noCaptureCount = 0;
        List<String> mismatchLines = new ArrayList<>();

        for (LocalDateTime tick : ticksFrom(latest)) {
            for (Market market : MARKETS) {
                Map<Long, List<CategoryTierBreakdown>> oldValue = marketMapCategoryChangeRateSnapshotService
                        .findTierBreakdownsByCategoryId(List.of(market), tick)
                        .getOrDefault(market, Map.of());
                if (oldValue.isEmpty()) {
                    noCaptureCount++;
                    continue;
                }
                comparedCount++;

                Map<Long, List<CategoryTierBreakdown>> newValue = categoryTierAggregationService.aggregateByCategory(
                        marketMapQueryService.getCustomMarketMapTree(market, tick));

                List<String> mismatchesForPair = compare(tick, market, oldValue, newValue);
                if (mismatchesForPair.isEmpty()) {
                    matchCount++;
                } else {
                    mismatchCount++;
                    mismatchLines.addAll(mismatchesForPair);
                }
            }
        }

        mismatchLines.forEach(System.out::println);
        System.out.printf(
                "[6절 비교 요약] 비교=%d 일치=%d 불일치=%d 캡처없음=%d%n", comparedCount, matchCount, mismatchCount, noCaptureCount);

        // 콘솔 출력만으로는 결과를 놓치기 쉽다(-i 로그가 길다) — BUILD 성공/실패로 드러나게 한다.
        // comparedCount가 0인 채 조용히 통과하는 경우(주말·공휴일, 다음 날 07:00 전, 이 PR 배포 뒤라
        // 저장이 멈춰 옛 값이 과거 날짜에만 남은 경우)를 특히 걸러야 한다.
        assertThat(comparedCount)
                .as("비교한 (시각, 마켓)이 없다 — 같은 거래일 07:00 이후, 이 PR 배포 전에 돌려야 한다")
                .isPositive();
        assertThat(mismatchCount).as("불일치 %d건 — 위 [불일치] 줄 참고", mismatchCount).isZero();
    }

    /** latest부터 collect.interval-minutes씩 뒤로 가며 TICK_COUNT개를 만들되, 오늘 07:00 이전은 뺀다 —
     * 종목정보가 매일 07:00에 동기화되므로, 그 이전 tick을 오늘 종목정보로 다시 계산하면 상장주식수가
     * 바뀐 종목 때문에 코드가 맞아도 불일치가 난다. */
    private List<LocalDateTime> ticksFrom(LocalDateTime latest) {
        LocalDateTime todayCutoff = KstClock.now().toLocalDate().atTime(7, 0);
        List<LocalDateTime> ticks = new ArrayList<>();
        for (int i = 0; i < TICK_COUNT; i++) {
            LocalDateTime tick = latest.minusMinutes((long) intervalMinutes * i);
            if (tick.isBefore(todayCutoff)) {
                continue;
            }
            ticks.add(tick);
        }
        return ticks;
    }

    private List<String> compare(
            LocalDateTime tick,
            Market market,
            Map<Long, List<CategoryTierBreakdown>> oldValue,
            Map<Long, List<CategoryTierBreakdown>> newValue) {
        List<String> lines = new ArrayList<>();
        if (!oldValue.keySet().equals(newValue.keySet())) {
            lines.add("[불일치] 시각=%s 마켓=%s 카테고리id집합다름 옛값=%s 새값=%s"
                    .formatted(tick, market, oldValue.keySet(), newValue.keySet()));
            return lines;
        }
        for (Map.Entry<Long, List<CategoryTierBreakdown>> entry : oldValue.entrySet()) {
            Long categoryId = entry.getKey();
            lines.addAll(compareCategory(tick, market, categoryId, entry.getValue(), newValue.get(categoryId)));
        }
        return lines;
    }

    private List<String> compareCategory(
            LocalDateTime tick,
            Market market,
            Long categoryId,
            List<CategoryTierBreakdown> oldBreakdowns,
            List<CategoryTierBreakdown> newBreakdowns) {
        List<String> lines = new ArrayList<>();
        Map<Long, CategoryTierBreakdown> oldByTier = toTierMap(oldBreakdowns);
        Map<Long, CategoryTierBreakdown> newByTier = toTierMap(newBreakdowns);
        if (!oldByTier.keySet().equals(newByTier.keySet())) {
            lines.add("[불일치] 시각=%s 마켓=%s 카테고리=%d 구간id집합다름 옛값=%s 새값=%s"
                    .formatted(tick, market, categoryId, oldByTier.keySet(), newByTier.keySet()));
            return lines;
        }
        for (Long tierId : oldByTier.keySet()) {
            lines.addAll(
                    compareBreakdown(tick, market, categoryId, tierId, oldByTier.get(tierId), newByTier.get(tierId)));
        }
        return lines;
    }

    private List<String> compareBreakdown(
            LocalDateTime tick,
            Market market,
            Long categoryId,
            Long tierId,
            CategoryTierBreakdown oldBreakdown,
            CategoryTierBreakdown newBreakdown) {
        List<String> lines = new ArrayList<>();
        compareField(
                tick,
                market,
                categoryId,
                tierId,
                "weightedSum",
                oldBreakdown.weightedSum(),
                newBreakdown.weightedSum(),
                lines);
        compareField(
                tick,
                market,
                categoryId,
                tierId,
                "totalValue",
                oldBreakdown.totalValue(),
                newBreakdown.totalValue(),
                lines);
        compareField(
                tick,
                market,
                categoryId,
                tierId,
                "simpleSum",
                oldBreakdown.simpleSum(),
                newBreakdown.simpleSum(),
                lines);
        if (!oldBreakdown.itemCount().equals(newBreakdown.itemCount())) {
            lines.add("[불일치] 시각=%s 마켓=%s 카테고리=%d 구간=%d 필드=itemCount 옛값=%d 새값=%d"
                    .formatted(tick, market, categoryId, tierId, oldBreakdown.itemCount(), newBreakdown.itemCount()));
        }
        return lines;
    }

    private void compareField(
            LocalDateTime tick,
            Market market,
            Long categoryId,
            Long tierId,
            String field,
            BigDecimal oldValue,
            BigDecimal newValue,
            List<String> lines) {
        if (oldValue.compareTo(newValue) != 0) {
            lines.add("[불일치] 시각=%s 마켓=%s 카테고리=%d 구간=%d 필드=%s 옛값=%s 새값=%s"
                    .formatted(tick, market, categoryId, tierId, field, oldValue, newValue));
        }
    }

    private Map<Long, CategoryTierBreakdown> toTierMap(List<CategoryTierBreakdown> breakdowns) {
        if (breakdowns == null) {
            return Map.of();
        }
        return breakdowns.stream().collect(Collectors.toMap(CategoryTierBreakdown::tierId, Function.identity()));
    }
}
