package dev.eolmae.marketmonitor.domain.stock;

import dev.eolmae.marketmonitor.domain.stock.config.CacheConfig;
import dev.eolmae.marketmonitor.domain.stock.repository.WatchStockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WatchStockCacheService {

    private final WatchStockRepository repository;

    @Cacheable(CacheConfig.WATCH_CODES)
    @Transactional(readOnly = true)
    public List<String> findDistinctStockCodes() {
        return repository.findDistinctStockCodes();
    }

    @CacheEvict(value = CacheConfig.WATCH_CODES, allEntries = true)
    public void evict() {}
}
