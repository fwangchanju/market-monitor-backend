package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.ProgramTradingDailyHistory;
import java.util.List;

public interface ProgramTradingDailyHistoryRepositoryCustom {

    List<ProgramTradingDailyHistory> findRecentByStockCode(String stockCode);
}
