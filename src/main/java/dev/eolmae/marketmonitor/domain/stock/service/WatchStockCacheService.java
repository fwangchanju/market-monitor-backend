package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.common.cache.CacheKey;
import dev.eolmae.marketmonitor.common.cache.CacheService;
import dev.eolmae.marketmonitor.domain.stock.entity.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.repository.WatchStockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WatchStockCacheService implements CacheService<List<WatchStock>> {

    private final WatchStockRepository watchStockRepository;

    @Override
    @Cacheable(CacheKey.WATCH_STOCK)
    @Transactional(readOnly = true)
    public List<WatchStock> getCache() {
        return watchStockRepository.findAll();
    }

    @Override
    @CacheEvict(value = CacheKey.WATCH_STOCK, allEntries = true)
    public void evict() {}
}
