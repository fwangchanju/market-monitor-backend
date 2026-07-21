package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.common.cache.CacheKey;
import dev.eolmae.marketmonitor.common.cache.CacheService;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.StockInfoRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockInfoCacheService implements CacheService<Map<String, StockInfo>> {

    private final StockInfoRepository stockInfoRepository;

    @Override
    @Cacheable(CacheKey.STOCK_INFO)
    @Transactional(readOnly = true)
    public Map<String, StockInfo> getCache() {
        return stockInfoRepository.findAll().stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
    }

    @Override
    @CacheEvict(value = CacheKey.STOCK_INFO, allEntries = true)
    public void evict() {}
}
