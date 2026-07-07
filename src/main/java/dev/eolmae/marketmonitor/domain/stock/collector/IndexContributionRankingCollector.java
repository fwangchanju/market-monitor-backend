package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import dev.eolmae.marketmonitor.common.util.StockCode;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorCurrentPriceRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorCurrentPriceResponse;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorPriceListRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorPriceListResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.IndexContributionRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketmonitor.domain.stock.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
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
import dev.eolmae.marketmonitor.common.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexContributionRankingCollector {

    /**
     * 지수기여도 연산
     * ka20001: 업종현재가요청
     * ka20002: 업종별주가요청
     * ka10099: stockInfoCache
     */
    private static final int TOP_RANKING_SIZE = 30;

    private final KiwoomApiClient kiwoomApiClient;
    private final StockInfoCacheService stockInfoCacheService;
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository;

    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();

        for (Market market : Market.values()) {
            try {
                collectForMarket(market, stockInfoCache, snapshotTime);
            } catch (Exception e) {
                log.error("지수기여도랭킹 수집 실패: market={}", market, e);
            }
        }
    }

    private void collectForMarket(Market market, Map<String, StockInfo> stockInfoCache, LocalDateTime snapshotTime) {
        String mrktTp = MrktTp.from(market);
        String indsCd = IndsCd.from(market);

        BigDecimal prevIndexValue = collectMarketOverview(market, mrktTp, indsCd, snapshotTime); // ka20001
        List<SectorPriceListResponse.StockItem> items = collectSectorPrice(market, mrktTp, indsCd, snapshotTime); // ka20002

        if (indexContributionRankingSnapshotRepository.existsBySnapshotTimeAndMarketType(snapshotTime, market)) {
            log.debug("지수기여도랭킹 이미 존재, 스킵: market={}, snapshotTime={}", market, snapshotTime);
            return;
        }

        computeAndSaveRanking(market, items, stockInfoCache, prevIndexValue, snapshotTime);
    }

    private BigDecimal collectMarketOverview(Market market, String mrktTp, String indsCd, LocalDateTime snapshotTime) {
        var request = new SectorCurrentPriceRequest(mrktTp, indsCd);
        SectorCurrentPriceResponse response = kiwoomApiClient.post(request, SectorCurrentPriceResponse.class);

        if (!marketOverviewSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, snapshotTime)) {
            marketOverviewSnapshotRepository.save(toMarketOverviewEntity(market, snapshotTime, response));
        }

        BigDecimal curPrc = KiwoomValueParser.parseBigDecimal(response.curPrc()).abs();
        BigDecimal predPre = KiwoomValueParser.parseBigDecimal(response.predPre());
        return curPrc.subtract(predPre);
    }

    private List<SectorPriceListResponse.StockItem> collectSectorPrice(
            Market market, String mrktTp, String indsCd, LocalDateTime snapshotTime) {

        String stexTp = StexType.KRX_NXT.code(); // KRX+NXT 합산
        var request = new SectorPriceListRequest(mrktTp, indsCd, stexTp);
        SectorPriceListResponse response = kiwoomApiClient.post(request, SectorPriceListResponse.class);

        if (response.items() == null || response.items().isEmpty()) {
            return List.of();
        }

        if (!sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, snapshotTime)) {
            for (SectorPriceListResponse.StockItem item : response.items()) {
                sectorPriceSnapshotRepository.save(toSectorPriceEntity(market, snapshotTime, item));
            }
        }

        return response.items();
    }

    private void computeAndSaveRanking(
            Market market, List<SectorPriceListResponse.StockItem> items,
            Map<String, StockInfo> stockInfoCache, BigDecimal prevIndexValue, LocalDateTime snapshotTime) {

        // 유효 종목 필터링 및 전일 시가총액 합산
        StockMarketCode marketCode = StockMarketCode.from(market);
        List<StockCandidate> candidates = new ArrayList<>();
        BigDecimal prevMarketCapitalization = BigDecimal.ZERO;
        for (SectorPriceListResponse.StockItem item : items) {
            String stockCode = StockCode.removeSuffix(item.stkCd());
            StockInfo stockInfo = stockInfoCache.get(stockCode);
            // API 응답 품질 문제로 필수 필드가 없는 종목 제외
            if (stockInfo == null || stockInfo.getLastPrice() == null || stockInfo.getListCount() == null) {
                continue;
            }
            // ELW, ETF 등 주권 외 종목 제외
            if (!marketCode.matches(stockInfo.getMarketCode())) {
                continue;
            }
            // 전일종가 × 상장주식수 = 종목별 전일 시가총액 합산
            prevMarketCapitalization =
                    prevMarketCapitalization.add(stockInfo.getLastPrice().multiply(BigDecimal.valueOf(stockInfo.getListCount())));
            candidates.add(new StockCandidate(
                    stockCode,
                    Strings.trimToEmpty(item.stkNm()),
                    stockInfo.getMarketCode(),
                    KiwoomValueParser.parseBigDecimal(item.curPrc()).abs(),
                    stockInfo.getLastPrice(),
                    BigDecimal.valueOf(stockInfo.getListCount())));
        }

        if (prevMarketCapitalization.signum() == 0) {
            throw new EscalateException(ErrorCode.PREV_MARKET_CAPITALIZATION_ZERO, market.name());
        }

        // 지수기여도 및 등락률 계산 후 상위 랭킹 추출
        final BigDecimal totalMarketCapitalization = prevMarketCapitalization;
        List<ScoredStock> scored = candidates.stream()
                .map(c -> {
                    BigDecimal priceChange = c.curPrice().subtract(c.prevPrice());
                    BigDecimal contribution = priceChange
                            .multiply(c.listCount())
                            .divide(totalMarketCapitalization, MathContext.DECIMAL128)
                            .multiply(prevIndexValue);
                    BigDecimal changeRate = BigDecimal.ZERO;
                    if (c.prevPrice().signum() != 0) {
                        changeRate = priceChange
                                .divide(c.prevPrice(), 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                    }
                    return new ScoredStock(c.stockCode(), c.stockName(), c.marketCode(), contribution, changeRate);
                })
                .sorted(Comparator.comparing(ScoredStock::contribution).reversed())
                .limit(TOP_RANKING_SIZE)
                .toList();

        // 랭킹 저장
        int rank = 1;
        for (ScoredStock stock : scored) {
            indexContributionRankingSnapshotRepository.save(toRankingEntity(market, snapshotTime, rank++, stock));
        }

        log.info("지수기여도랭킹 수집 완료: market={}, 저장건수={}", market, scored.size());
    }

    private static MarketOverviewSnapshot toMarketOverviewEntity(
            Market market, LocalDateTime snapshotTime, SectorCurrentPriceResponse response) {
        return MarketOverviewSnapshot.create(
                market,
                snapshotTime,
                KiwoomValueParser.parseBigDecimal(response.curPrc()).abs(),
                KiwoomValueParser.parseBigDecimal(response.predPre()),
                KiwoomValueParser.parseBigDecimal(response.fluRt()),
                KiwoomValueParser.parseBigDecimal(response.trdePrica()),
                Strings.trimToEmpty(response.mrktStatClsCode()),
                KiwoomValueParser.parseInt(response.rising()),
                KiwoomValueParser.parseInt(response.fall()),
                KiwoomValueParser.parseInt(response.stdns()),
                KiwoomValueParser.parseInt(response.upl()),
                KiwoomValueParser.parseInt(response.lst()),
                LocalDateTime.now(Zone.KST.zoneId()));
    }

    private static SectorPriceSnapshot toSectorPriceEntity(
            Market market, LocalDateTime snapshotTime, SectorPriceListResponse.StockItem item) {
        return SectorPriceSnapshot.create(
                market,
                snapshotTime,
                StockCode.removeSuffix(item.stkCd()),
                Strings.trimToEmpty(item.stkNm()),
                KiwoomValueParser.parseBigDecimal(item.curPrc()).abs(),
                KiwoomValueParser.parseBigDecimal(item.predPre()),
                KiwoomValueParser.parseBigDecimal(item.fluRt()));
    }

    private static IndexContributionRankingSnapshot toRankingEntity(
            Market market, LocalDateTime snapshotTime, int rank, ScoredStock stock) {
        return IndexContributionRankingSnapshot.create(
                market,
                snapshotTime,
                rank,
                stock.stockCode(),
                stock.stockName(),
                stock.marketCode(),
                stock.contribution().setScale(4, RoundingMode.HALF_UP),
                stock.changeRate().setScale(4, RoundingMode.HALF_UP));
    }

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("1"); // ka20001/ka20002 mrkt_tp
        final String value;

        MrktTp(String value) {
            this.value = value;
        }

        static String from(Market market) {
            return switch (market) {
                case KOSPI -> KOSPI.value;
                case KOSDAQ -> KOSDAQ.value;
            };
        }
    }

    private enum IndsCd {
        KOSPI("001"),
        KOSDAQ("101"); // ka20001/ka20002 inds_cd
        final String value;

        IndsCd(String value) {
            this.value = value;
        }

        static String from(Market market) {
            return switch (market) {
                case KOSPI -> KOSPI.value;
                case KOSDAQ -> KOSDAQ.value;
            };
        }
    }

    private record StockCandidate(
            String stockCode, String stockName, String marketCode,
            BigDecimal curPrice, BigDecimal prevPrice, BigDecimal listCount) {}

    private record ScoredStock(
            String stockCode, String stockName, String marketCode, BigDecimal contribution, BigDecimal changeRate) {}
}
