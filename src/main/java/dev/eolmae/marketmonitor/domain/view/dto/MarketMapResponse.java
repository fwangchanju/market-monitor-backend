package dev.eolmae.marketmonitor.domain.view.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SnapshotResponse<T>를 그대로 못 쓰는 이유: marketOverview는 트리 items와 별개로 마켓 전체 단위 값 하나라,
 * 여러 엔드포인트가 공유하는 제네릭 래퍼에 이 필드만을 위해 얹을 수 없다. market이 ALL_STOCK처럼 마켓
 * 여럿을 합친 조회면 단일 지수값이 없으므로 marketOverview는 null.
 */
public record MarketMapResponse(
        LocalDateTime snapshotTime, List<MarketMapCategoryNode> items, MarketOverviewItem marketOverview) {
    public static MarketMapResponse empty() {
        return new MarketMapResponse(null, List.of(), null);
    }
}
