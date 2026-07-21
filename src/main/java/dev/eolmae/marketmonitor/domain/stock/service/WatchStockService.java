package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.enums.RegisterBy;
import dev.eolmae.marketmonitor.domain.stock.repository.WatchStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WatchStockService {

    private final WatchStockRepository watchStockRepository;
    private final WatchStockCacheService watchStockCacheService;
    private final WatchStockBackfillService watchStockBackfillService;

    public void register(String stockCode) {
        if (watchStockRepository.findByStockCode(stockCode).isPresent()) {
            return;
        }
        WatchStock watchStock = watchStockRepository.save(WatchStock.createManual(stockCode));
        watchStockCacheService.evict();
        watchStockBackfillService.backfill(watchStock);
    }

    public void unregister(String stockCode) {
        watchStockRepository
                .findByStockCode(stockCode)
                .filter(watchStock -> watchStock.getRegisterBy() == RegisterBy.USER)
                .ifPresent(watchStock -> {
                    watchStockRepository.delete(watchStock);
                    watchStockCacheService.evict();
                });
    }

    public void designateAsPrimary(String stockCode) {
        WatchStock target = watchStockRepository
                .findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, stockCode));
        watchStockRepository.findByIsPrimaryTrue().ifPresent(WatchStock::clearPrimary);
        target.designateAsPrimary();
        watchStockCacheService.evict();
    }

    /** 관심종목에 없으면 등록까지 함께 처리 후 대표로 지정 */
    public void registerAsPrimary(String stockCode) {
        register(stockCode);
        designateAsPrimary(stockCode);
    }
}
