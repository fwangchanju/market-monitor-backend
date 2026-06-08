package dev.eolmae.marketmonitor.collector;

import dev.eolmae.marketmonitor.common.enums.Board;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.StockMarketCode;
import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.common.util.StockCode;
import dev.eolmae.marketmonitor.domain.dashboard.IndexContributionRankingSnapshot;
import dev.eolmae.marketmonitor.domain.dashboard.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.dashboard.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.dashboard.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.StockMaster;
import dev.eolmae.marketmonitor.domain.stock.StockMasterCacheService;
import dev.eolmae.marketmonitor.exception.EscalateException;
import dev.eolmae.marketmonitor.external.kiwoom.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Ka20002Request;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Ka20002Response;
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

    private static final int TOP_N = 50;

    private final KiwoomApiClient kiwoomApiClient;
    private final StockMasterCacheService stockMasterCacheService;
    private final IndexContributionRankingSnapshotRepository snapshotRepository;
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;

    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        Map<String, StockMaster> masterMap = stockMasterCacheService.findAllAsMap();

        for (Board board : Board.values()) {
            try {
                collectForMarket(board, masterMap, snapshotTime);
            } catch (Exception e) {
                log.error("지수기여도랭킹 수집 실패: board={}", board, e);
            }
        }
    }

    private void collectForMarket(Board board, Map<String, StockMaster> masterMap, LocalDateTime snapshotTime) {
        Exchange marketType = Exchange.valueOf(board.name());
        StockMarketCode marketCode = StockMarketCode.valueOf(board.name());
        String mrktTp =
                switch (board) {
                    case KOSPI -> MrktTp.KOSPI.value;
                    case KOSDAQ -> MrktTp.KOSDAQ.value;
                };
        String indsCd =
                switch (board) {
                    case KOSPI -> IndsCd.KOSPI.value;
                    case KOSDAQ -> IndsCd.KOSDAQ.value;
                };

        List<IndexContributionRankingSnapshot> existing =
                snapshotRepository.findBySnapshotTimeAndMarketTypeOrderByRankAsc(snapshotTime, marketType);
        if (!existing.isEmpty()) {
            log.debug("지수기여도랭킹 이미 존재, 스킵: board={}, snapshotTime={}", board, snapshotTime);
            return;
        }

        var request = new Ka20002Request(mrktTp, indsCd, "3");
        Ka20002Response response = kiwoomApiClient.post(request, Ka20002Response.class);
        List<Ka20002Response.StockItem> items = response.items() != null ? response.items() : List.of();

        BigDecimal prevIndexValue = resolvePrevIndexValue(marketType);

        BigDecimal prevTotalMarketCap = BigDecimal.ZERO;
        for (Ka20002Response.StockItem item : items) {
            StockMaster master = masterMap.get(StockCode.removeSuffix(item.stkCd()));
            if (!isValidForMarket(master, marketCode)) {
                continue;
            }
            prevTotalMarketCap =
                    prevTotalMarketCap.add(master.getLastPrice().multiply(BigDecimal.valueOf(master.getListCount())));
        }

        if (prevTotalMarketCap.compareTo(BigDecimal.ZERO) == 0) {
            throw new EscalateException("전일 전체 시가총액 합산 결과가 0: board=" + board);
        }

        List<ScoredStock> scored = new ArrayList<>();
        for (Ka20002Response.StockItem item : items) {
            String stockCode = StockCode.removeSuffix(item.stkCd());
            StockMaster master = masterMap.get(stockCode);
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

        log.info("지수기여도랭킹 수집 완료: board={}, 저장건수={}", board, rank - 1);
    }

    private boolean isValidForMarket(StockMaster master, StockMarketCode marketCode) {
        return master != null
                && master.getLastPrice() != null
                && master.getListCount() != null
                && marketCode.matches(master.getMarketCode());
    }

    private BigDecimal resolvePrevIndexValue(Exchange marketType) {
        MarketOverviewSnapshot snapshot = marketOverviewSnapshotRepository
                .findTopByMarketTypeOrderBySnapshotTimeDesc(marketType)
                .orElseThrow(() ->
                        new EscalateException("MarketOverviewSnapshot 데이터 없음 — 지수기여도 연산 불가: market=" + marketType));
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
