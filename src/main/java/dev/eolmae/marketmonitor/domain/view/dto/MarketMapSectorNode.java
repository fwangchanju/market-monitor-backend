package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketMapSectorNode(
        Long sectorId,
        String sectorName,
        boolean isExcluded,
        BigDecimal totalMarketValue,
        List<MarketMapSectorNode> children,
        List<MarketMapItem> items) {

    // 기본 마켓맵 노드용 — exclude 대상 아님, 변화율 미계산, 자식 없음(1뎁스).
    public static MarketMapSectorNode leaf(
            Long sectorId, String sectorName, BigDecimal totalMarketValue, List<MarketMapItem> items) {
        return new MarketMapSectorNode(sectorId, sectorName, false, totalMarketValue, List.of(), items);
    }
}
