package dev.eolmae.marketmonitor.domain.stock;

import dev.eolmae.marketmonitor.domain.stock.config.CacheConfig;
import dev.eolmae.marketmonitor.domain.stock.repository.StockMasterRepository;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockMasterCacheService {

    private final StockMasterRepository repository;

    @Cacheable(CacheConfig.STOCK_MASTER)
    @Transactional(readOnly = true)
    public Map<String, StockMaster> findAllAsMap() {
        return repository.findAll().stream().collect(Collectors.toMap(StockMaster::getStockCode, s -> s));
    }

    @CacheEvict(value = CacheConfig.STOCK_MASTER, allEntries = true)
    public void evict() {}
}
