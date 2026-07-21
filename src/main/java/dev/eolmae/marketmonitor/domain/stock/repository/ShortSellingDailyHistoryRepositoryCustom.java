package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.ShortSellingDailyHistory;
import java.util.List;

public interface ShortSellingDailyHistoryRepositoryCustom {

    List<ShortSellingDailyHistory> findRecentByStockCode(String stockCode);
}
