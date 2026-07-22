package dev.eolmae.marketmonitor.domain.view.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketMapExcludedStock;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockCategory;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketMapExcludedStockRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.StockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.util.CollectionChecker;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryGroup;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapItem;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketMapQueryService {

    private static final String UNCATEGORIZED = "미분류";

    private final StockInfoCacheService stockInfoCacheService;
    private final StockCategoryRepository stockCategoryRepository;
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository;

    /** 마켓맵: 카테고리별로 묶은 종목 시가총액(박스 크기용)/등락률(색상용) 반환 */
    public SnapshotResponse<MarketMapCategoryGroup> getMarketMap(Market market, boolean isExclude) {
        LocalDateTime latestSnapshotTime = sectorPriceSnapshotRepository
                .findFirstByMarketTypeOrderBySnapshotTimeDesc(market)
                .map(SectorPriceSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        List<StockInfo> candidates = filterCandidates(market, isExclude);
        Map<String, StockCategory> categoryMap = stockCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(StockCategory::getStockCode, Function.identity()));
        Map<String, SectorPriceSnapshot> priceMap =
                sectorPriceSnapshotRepository.findByMarketTypeAndSnapshotTime(market, latestSnapshotTime).stream()
                        .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));

        Map<String, List<MarketMapItem>> grouped = candidates.stream()
                .collect(Collectors.groupingBy(
                        stockInfo -> resolveCategoryName(stockInfo, categoryMap),
                        Collectors.mapping(
                                stockInfo -> toMarketMapItem(stockInfo, priceMap, latestSnapshotTime),
                                Collectors.toList())));

        List<MarketMapCategoryGroup> groups = grouped.entrySet().stream()
                .map(entry -> new MarketMapCategoryGroup(entry.getKey(), entry.getValue()))
                .toList();

        return new SnapshotResponse<>(CollectionChecker.expectedSnapshotTime(), groups);
    }

    private List<StockInfo> filterCandidates(Market market, boolean isExclude) {
        StockMarketCode marketCode = StockMarketCode.from(market);

        List<StockInfo> candidates = stockInfoCacheService.getCache().values().stream()
                .filter(stockInfo -> stockInfo.getMarketType() == market)
                .filter(StockInfo::isActive)
                .filter(stockInfo -> marketCode.matches(stockInfo.getMarketCode()))
                .toList();

        if (!isExclude) {
            return candidates;
        }

        Set<String> excludedCodes = marketMapExcludedStockRepository.findByIsActiveTrue().stream()
                .map(MarketMapExcludedStock::getStockCode)
                .collect(Collectors.toSet());

        return candidates.stream()
                .filter(stockInfo -> !excludedCodes.contains(stockInfo.getStockCode()))
                .toList();
    }

    private String resolveCategoryName(StockInfo stockInfo, Map<String, StockCategory> categoryMap) {
        StockCategory category = categoryMap.get(stockInfo.getStockCode());
        String categoryName = category != null ? category.getCategoryName() : stockInfo.getCategoryName();
        if (categoryName == null || categoryName.isBlank()) {
            return UNCATEGORIZED;
        }
        return categoryName;
    }

    private MarketMapItem toMarketMapItem(
            StockInfo stockInfo, Map<String, SectorPriceSnapshot> priceMap, LocalDateTime snapshotTime) {
        BigDecimal currentPrice = BigDecimal.ZERO;
        BigDecimal changeRate = BigDecimal.ZERO;
        SectorPriceSnapshot priceSnapshot = priceMap.get(stockInfo.getStockCode());
        if (priceSnapshot != null) {
            currentPrice = priceSnapshot.getCurrentPrice();
            changeRate = priceSnapshot.getChangeRate();
        }
        BigDecimal totalMarketValue = currentPrice.multiply(BigDecimal.valueOf(stockInfo.getListCount()));

        return new MarketMapItem(
                stockInfo.getStockCode(), stockInfo.getStockName(), totalMarketValue, changeRate, snapshotTime);
    }
}
