package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.common.util.StockCode;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorPriceListRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorPriceListResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.IndexContributionRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketmonitor.domain.stock.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@ConditionalOnProperty(name = "stock.collection.enabled", havingValue = "true")
@RequiredArgsConstructor
public class IndexContributionRankingCollector {

    /**
     * 지수기여도 연산
     * ka20001: 업종별주가요청
     * ka20002: 업종별주가요청
     * ka10099
     *
     */
    private static final int TOP_N = 50;

    private final KiwoomApiClient kiwoomApiClient;
    private final StockInfoCacheService stockInfoCacheService;
    private final IndexContributionRankingSnapshotRepository snapshotRepository;
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;

    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        Map<String, StockInfo> masterMap = stockInfoCacheService.getCache();

        for (Market marketType : Market.values()) {
            try {
                collectForMarket(marketType, masterMap, snapshotTime);
            } catch (Exception e) {
                log.error("지수기여도랭킹 수집 실패: marketType={}", marketType, e);
            }
        }
    }

    private void collectForMarket(Market marketType, Map<String, StockInfo> masterMap, LocalDateTime snapshotTime) {
        StockMarketCode marketCode = StockMarketCode.valueOf(marketType.name());
        String mrktTp =
                switch (marketType) {
                    case KOSPI -> MrktTp.KOSPI.value;
                    case KOSDAQ -> MrktTp.KOSDAQ.value;
                };
        String indsCd =
                switch (marketType) {
                    case KOSPI -> IndsCd.KOSPI.value;
                    case KOSDAQ -> IndsCd.KOSDAQ.value;
                };

        List<IndexContributionRankingSnapshot> existing =
                snapshotRepository.findBySnapshotTimeAndMarketTypeOrderByRankAsc(snapshotTime, marketType);
        if (!existing.isEmpty()) {
            log.debug("지수기여도랭킹 이미 존재, 스킵: marketType={}, snapshotTime={}", marketType, snapshotTime);
            return;
        }

        var request = new SectorPriceListRequest(mrktTp, indsCd, "3");
        SectorPriceListResponse response = kiwoomApiClient.post(request, SectorPriceListResponse.class);
        List<SectorPriceListResponse.StockItem> items = response.items() != null ? response.items() : List.of();

        BigDecimal prevIndexValue = resolvePrevIndexValue(marketType);

        BigDecimal prevTotalMarketCap = BigDecimal.ZERO;
        for (SectorPriceListResponse.StockItem item : items) {
            StockInfo master = masterMap.get(StockCode.removeSuffix(item.stkCd()));
            if (!isValidForMarket(master, marketCode)) {
                continue;
            }
            prevTotalMarketCap =
                    prevTotalMarketCap.add(master.getLastPrice().multiply(BigDecimal.valueOf(master.getListCount())));
        }

        if (prevTotalMarketCap.compareTo(BigDecimal.ZERO) == 0) {
            throw new EscalateException(ErrorCode.PREV_MARKET_CAP_ZERO, marketType.name());
        }

        List<ScoredStock> scored = new ArrayList<>();
        for (SectorPriceListResponse.StockItem item : items) {
            String stockCode = StockCode.removeSuffix(item.stkCd());
            StockInfo master = masterMap.get(stockCode);
            if (!isValidForMarket(master, marketCode)) {
                continue;
            }

            BigDecimal curPrice = NumberParser.parseBigDecimal(item.curPrc()).abs();
            BigDecimal prevPrice = master.getLastPrice();
            BigDecimal listCount = BigDecimal.valueOf(master.getListCount());

            BigDecimal contribution = curPrice.subtract(prevPrice)
                    .multiply(listCount)
                    .divide(prevTotalMarketCap, MathContext.DECIMAL128)
                    .multiply(prevIndexValue);

            BigDecimal changeRate = prevPrice.compareTo(BigDecimal.ZERO) != 0
                    ? curPrice.subtract(prevPrice)
                            .divide(prevPrice, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            scored.add(new ScoredStock(stockCode, item.stkNm(), master.getMarketCode(), contribution, changeRate));
        }

        scored.sort(Comparator.comparing(ScoredStock::contribution).reversed());

        int rank = 1;
        for (ScoredStock stock : scored.subList(0, Math.min(TOP_N, scored.size()))) {
            snapshotRepository.save(IndexContributionRankingSnapshot.create(
                    marketType,
                    rank++,
                    stock.stockCode(),
                    stock.stockName(),
                    stock.marketCode(),
                    stock.contribution().setScale(4, RoundingMode.HALF_UP),
                    stock.changeRate().setScale(4, RoundingMode.HALF_UP),
                    snapshotTime));
        }

        log.info("지수기여도랭킹 수집 완료: marketType={}, 저장건수={}", marketType, rank - 1);
    }

    private boolean isValidForMarket(StockInfo master, StockMarketCode marketCode) {
        return master != null
                && master.getLastPrice() != null
                && master.getListCount() != null
                && marketCode.matches(master.getMarketCode());
    }

    private BigDecimal resolvePrevIndexValue(Market marketType) {
        MarketOverviewSnapshot snapshot = marketOverviewSnapshotRepository
                .findTopByMarketTypeOrderBySnapshotTimeDesc(marketType)
                .orElseThrow(() -> new EscalateException(ErrorCode.BASE_SNAPSHOT_NOT_FOUND, marketType.name()));
        return snapshot.getIndexValue().subtract(snapshot.getChangeValue());
    }

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("1");
        final String value;

        MrktTp(String value) {
            this.value = value;
        }
    }

    private enum IndsCd {
        KOSPI("001"),
        KOSDAQ("101");
        final String value;

        IndsCd(String value) {
            this.value = value;
        }
    }

    private record ScoredStock(
            String stockCode, String stockName, String marketCode, BigDecimal contribution, BigDecimal changeRate) {}
}
