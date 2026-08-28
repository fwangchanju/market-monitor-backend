package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategoryChangeRateSnapshot;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryChangeRateSnapshotRepository;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마켓맵 카테고리별(하위 카테고리 재귀 포함) 가중평균/산술평균 등락률 스냅샷 저장. 트리 자체는 호출부가
 * MarketMapQueryService로 이미 만들어서 넘겨준다 — 이 서비스가 MarketMapQueryService를 직접 의존하면,
 * MarketMapQueryService가 최신 스냅샷 값을 읽어 응답에 채워 넣을 때(반대 방향 의존) 순환 참조가 된다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketMapCategoryChangeRateSnapshotService {

    private static final int SCALE = 4;

    private final MarketMapCategoryChangeRateSnapshotRepository marketMapCategoryChangeRateSnapshotRepository;

    @Transactional
    public void captureSnapshot(Market market, LocalDateTime snapshotTime, List<MarketMapCategoryNode> tree) {
        if (tree.isEmpty()) {
            return;
        }
        List<MarketMapCategoryChangeRateSnapshot> snapshots = new ArrayList<>();
        collectSnapshots(tree, market, snapshotTime, snapshots);
        marketMapCategoryChangeRateSnapshotRepository.saveAll(snapshots);
    }

    /** 라이브 조회용 — 최신 시각을 먼저 찾은 다음 그 시각으로 findRanking을 호출한다. */
    public SnapshotResponse<CategoryChangeRateItem> findLatestRanking(Market market, int beforeMinutes) {
        return marketMapCategoryChangeRateSnapshotRepository
                .findFirstByMarketTypeOrderBySnapshotTimeDesc(market)
                .map(latest -> findRanking(market, latest.getSnapshotTime(), beforeMinutes))
                .orElseGet(SnapshotResponse::empty);
    }

    /**
     * 카테고리별 현재(snapshotTime)/직전(beforeMinutes분 전) 등락률 평균 랭킹 조회. 호출부가 이미 알고 있는
     * 정확한 snapshotTime을 받는다 — 텔레그램 캡션처럼 특정 시각이 이미 정해진 호출부용. beforeMinutes
     * 시각에 정확히 일치하는 스냅샷이 없으면(장 시작 직후, 수집 gap 등) 해당 카테고리는 before 없이
     * 내려준다 — 가장 가까운 다른 시점 데이터로 조용히 대체하지 않는다.
     */
    public SnapshotResponse<CategoryChangeRateItem> findRanking(Market market, LocalDateTime snapshotTime, int beforeMinutes) {
        List<MarketMapCategoryChangeRateSnapshot> nowRows =
                marketMapCategoryChangeRateSnapshotRepository.findByMarketTypeAndSnapshotTime(market, snapshotTime);
        if (nowRows.isEmpty()) {
            return SnapshotResponse.empty();
        }

        LocalDateTime beforeTime = snapshotTime.minusMinutes(beforeMinutes);
        Map<Long, MarketMapCategoryChangeRateSnapshot> beforeByCategoryId =
                marketMapCategoryChangeRateSnapshotRepository.findByMarketTypeAndSnapshotTime(market, beforeTime).stream()
                        .collect(Collectors.toMap(MarketMapCategoryChangeRateSnapshot::getCategoryId, Function.identity()));

        List<CategoryChangeRateItem> items = nowRows.stream()
                .map(nowRow -> toItem(nowRow, beforeByCategoryId.get(nowRow.getCategoryId())))
                .toList();
        return new SnapshotResponse<>(snapshotTime, items);
    }

    private CategoryChangeRateItem toItem(
            MarketMapCategoryChangeRateSnapshot nowRow, MarketMapCategoryChangeRateSnapshot beforeRow) {
        SnapshotAverages now = new SnapshotAverages(nowRow.getWeightedAvgChangeRate(), nowRow.getSimpleAvgChangeRate());
        if (beforeRow == null) {
            return CategoryChangeRateItem.withoutBefore(nowRow.getCategoryId(), now);
        }
        SnapshotAverages before = new SnapshotAverages(beforeRow.getWeightedAvgChangeRate(), beforeRow.getSimpleAvgChangeRate());
        return CategoryChangeRateItem.withBefore(nowRow.getCategoryId(), now, before);
    }

    private void collectSnapshots(
            List<MarketMapCategoryNode> nodes,
            Market market,
            LocalDateTime snapshotTime,
            List<MarketMapCategoryChangeRateSnapshot> out) {
        for (MarketMapCategoryNode node : nodes) {
            Averages averages = computeAverages(collectItems(node));
            out.add(MarketMapCategoryChangeRateSnapshot.create(
                    market, node.categoryId(), snapshotTime, averages.weighted(), averages.simple()));
            collectSnapshots(node.children(), market, snapshotTime, out);
        }
    }

    private List<MarketMapItem> collectItems(MarketMapCategoryNode node) {
        List<MarketMapItem> items = new ArrayList<>(node.items());
        for (MarketMapCategoryNode child : node.children()) {
            items.addAll(collectItems(child));
        }
        return items;
    }

    // 시가총액 가중평균과 종목 수 기준 산술평균을 한 번의 순회로 같이 구한다.
    private Averages computeAverages(List<MarketMapItem> items) {
        if (items.isEmpty()) {
            return new Averages(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal simpleSum = BigDecimal.ZERO;
        for (MarketMapItem item : items) {
            totalWeight = totalWeight.add(item.totalMarketValue());
            weightedSum = weightedSum.add(item.changeRate().multiply(item.totalMarketValue()));
            simpleSum = simpleSum.add(item.changeRate());
        }
        BigDecimal weighted = totalWeight.signum() == 0
                ? BigDecimal.ZERO
                : weightedSum.divide(totalWeight, SCALE, RoundingMode.HALF_UP);
        BigDecimal simple = simpleSum.divide(BigDecimal.valueOf(items.size()), SCALE, RoundingMode.HALF_UP);
        return new Averages(weighted, simple);
    }

    private record Averages(BigDecimal weighted, BigDecimal simple) {}
}
