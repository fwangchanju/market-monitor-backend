package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.domain.stock.entity.MarketMapExcludedStock;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketMapExcludedStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MarketMapExcludedStockService {

    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository;

    public void register(String stockCode) {
        marketMapExcludedStockRepository
                .findById(stockCode)
                .ifPresentOrElse(
                        MarketMapExcludedStock::activate,
                        () -> marketMapExcludedStockRepository.save(MarketMapExcludedStock.create(stockCode)));
    }

    public void unregister(String stockCode) {
        marketMapExcludedStockRepository.findById(stockCode).ifPresent(MarketMapExcludedStock::deactivate);
    }

    public void deleteAll() {
        marketMapExcludedStockRepository.deleteAllInBatch();
    }
}
