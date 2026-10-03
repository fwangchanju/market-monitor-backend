package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.ShortSellingDailyHistory;
import java.util.List;

public interface ShortSellingDailyHistoryRepositoryCustom {

    List<ShortSellingDailyHistory> findRecentByStockCode(String stockCode);
}
