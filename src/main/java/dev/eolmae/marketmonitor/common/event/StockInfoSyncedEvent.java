package dev.eolmae.marketmonitor.common.event;

import java.util.List;

/** stock_info 동기화 완료 이벤트. marketmap이 이미 stock을 의존하고 있어 stock -> marketmap 직접 참조 시 순환 의존이 생기므로 이벤트로 분리. */
public record StockInfoSyncedEvent(List<NewStock> newStocks) {
    public record NewStock(String stockCode, String categoryName) {}
}
