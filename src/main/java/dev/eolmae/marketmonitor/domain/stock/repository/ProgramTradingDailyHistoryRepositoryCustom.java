package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingDailyHistory;
import java.util.List;

public interface ProgramTradingDailyHistoryRepositoryCustom {

    List<ProgramTradingDailyHistory> findRecentByStockCode(String stockCode);
}
