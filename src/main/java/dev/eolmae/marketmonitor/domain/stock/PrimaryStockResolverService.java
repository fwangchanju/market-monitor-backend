package dev.eolmae.marketmonitor.domain.stock;

import dev.eolmae.marketmonitor.domain.stock.collector.HoldingsSyncService;
import dev.eolmae.marketmonitor.domain.stock.repository.WatchStockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrimaryStockResolverService {

    private static final String DEFAULT_USER_KEY = "default";

    private final WatchStockRepository watchStockRepository;
    private final HoldingsCache holdingsCache;
    private final HoldingsSyncService holdingsSyncService;

    /**
     * 최우선 종목 결정:
     * 1. is_primary=true 관심종목 → 해당 종목코드
     * 2. holdingsCache 비중 1위 종목
     * 3. 캐시 미스 → kt00018 재호출 후 비중 1위
     */
    @Transactional(readOnly = true)
    public String resolveStockCode() {
        List<WatchStock> primaries = watchStockRepository.findByUserUserKeyAndIsPrimaryTrue(DEFAULT_USER_KEY);
        if (!primaries.isEmpty()) {
            return primaries.getFirst().getStock().getStockCode();
        }

        String topCode = holdingsCache.topStockCode();
        if (topCode != null) {
            return topCode;
        }

        log.debug("holdingsCache 비어있음 — kt00018 재호출");
        holdingsSyncService.sync();
        return holdingsCache.topStockCode();
    }
}
