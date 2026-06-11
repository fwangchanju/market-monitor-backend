package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.config.CacheConfig;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.StockInfoRepository;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockInfoCacheService {

    private final StockInfoRepository repository;

    @Cacheable(CacheConfig.STOCK_INFO)
    @Transactional(readOnly = true)
    public Map<String, StockInfo> findAllAsMap() {
        return repository.findAll().stream().collect(Collectors.toMap(StockInfo::getStockCode, s -> s));
    }

    @CacheEvict(value = CacheConfig.STOCK_INFO, allEntries = true)
    public void evict() {}
}
