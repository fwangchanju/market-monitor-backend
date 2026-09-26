package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.util.StockCode;
import dev.eolmae.marketmonitor.common.util.Strings;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorCurrentPriceRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorCurrentPriceResponse;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorPriceListRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorPriceListResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.IndexContributionRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.enums.ExchangeType;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.enums.StockMarketCode;
import dev.eolmae.marketmonitor.domain.stock.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final SectorPriceCacheService sectorPriceCacheService;
    private final TransactionTemplate transactionTemplate;

    // 마켓별로 독립된 트랜잭션. 하나 실패하면 그대로 예외를 던져서(catch 안 함) 이후 마켓은 시도하지 않고,
    // 호출부(CollectionScheduler.run())가 한 곳에서만 escalate한다.
    public void collect(LocalDateTime snapshotTime) {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();

        for (Market market : Market.values()) {
            transactionTemplate.executeWithoutResult(status -> collectForMarket(market, stockInfoCache, snapshotTime));
            warmSectorPriceCache(market, snapshotTime);
        }
    }

    // 결정 5의 캐시 적재(워밍) — 마켓 트랜잭션이 커밋된 직후, 그 마켓에 대해 캐시 메서드를 한 번 불러
    // DB에서 다시 읽어 캐시에 채운다. collectSectorPrice의 API 응답을 직접 넣지 않는 이유는, 그 메서드가
    // 행이 이미 있으면 저장을 건너뛰면서도 API 응답은 그대로 돌려주기 때문이다(응답과 DB가 다를 수 있음).
    // 적재는 성능 최적화지 정확성 조건이 아니라, 실패해도 수집 자체를 실패로 만들면 안 된다 — 읽을 때
    // 캐시에 없으면 DB에서 그냥 읽는다.
    private void warmSectorPriceCache(Market market, LocalDateTime snapshotTime) {
        try {
            sectorPriceCacheService.getCache(market, snapshotTime);
        } catch (Exception e) {
            log.warn("섹터 가격 캐시 적재 실패: market={}, snapshotTime={}", market, snapshotTime, e);
        }
    }

    private void collectForMarket(Market market, Map<String, StockInfo> stockInfoCache, LocalDateTime snapshotTime) {
        String mrktTp = MrktTp.from(market);
        String indsCd = IndsCd.from(market);

        BigDecimal prevIndexValue = collectMarketOverview(market, mrktTp, indsCd, snapshotTime); // ka20001
        List<SectorPriceListResponse.StockItem> items =
                collectSectorPrice(market, mrktTp, indsCd, snapshotTime, stockInfoCache); // ka20002

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

    private List<SectorPriceListResponse.StockItem> collectSectorPrice(
            Market market,
            String mrktTp,
            String indsCd,
            LocalDateTime snapshotTime,
            Map<String, StockInfo> stockInfoCache) {

        String stexTp = StexType.KRX_NXT.code(); // KRX+NXT 합산
        var request = new SectorPriceListRequest(mrktTp, indsCd, stexTp);
        SectorPriceListResponse response = kiwoomApiClient.post(request, SectorPriceListResponse.class);

        if (response.items() == null || response.items().isEmpty()) {
            return List.of();
        }

        if (!sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, snapshotTime)) {
            List<SectorPriceSnapshot> entities = new ArrayList<>();
            for (SectorPriceListResponse.StockItem item : response.items()) {
                // 화면·지수기여도는 코스피·코스닥 주권만 쓰므로 ELW·ETF 등과 stock_info에 없는 신규
                // 상장 종목은 저장하지 않는다
                StockInfo stockInfo = stockInfoCache.get(StockCode.removeSuffix(item.stkCd()));
                if (stockInfo == null || !StockMarketCode.isOrdinaryShare(stockInfo.getMarketCode())) {
                    continue;
                }
                entities.add(toSectorPriceEntity(market, snapshotTime, item));
            }
            sectorPriceSnapshotRepository.saveAll(entities);
        }

        return response.items();
    }

    private static SectorPriceSnapshot toSectorPriceEntity(
            Market market, LocalDateTime snapshotTime, SectorPriceListResponse.StockItem item) {
        return SectorPriceSnapshot.create(
                market,
                snapshotTime,
                StockCode.removeSuffix(item.stkCd()),
                ExchangeType.from(item.stkCd()),
                Strings.trimToEmpty(item.stkNm()),
                KiwoomValueParser.parseBigDecimal(item.curPrc()).abs(),
                KiwoomValueParser.parseBigDecimal(item.predPre()),
                KiwoomValueParser.parseBigDecimal(item.fluRt()));
    }

    private void computeAndSaveRanking(
            Market market,
            List<SectorPriceListResponse.StockItem> items,
            Map<String, StockInfo> stockInfoCache,
            BigDecimal prevIndexValue,
            LocalDateTime snapshotTime) {

        // 같은 종목이 거래소(KRX/NXT/SOR)별로 여러 줄 올 수 있음 — 현재가는 합산 대상이 아니라
        // 시가총액/기여도 중복 계산을 막기 위해 종목당 KRX 줄을 우선 채택(없으면 먼저 나온 줄)
        Map<String, SectorPriceListResponse.StockItem> selectedByStock = new HashMap<>();
        for (SectorPriceListResponse.StockItem item : items) {
            String stockCode = StockCode.removeSuffix(item.stkCd());
            SectorPriceListResponse.StockItem existing = selectedByStock.get(stockCode);
            if (existing == null || ExchangeType.from(item.stkCd()).isKrx()) {
                selectedByStock.put(stockCode, item);
            }
        }

        // 유효 종목 필터링 및 전일 시가총액 합산
        List<StockCandidate> candidates = new ArrayList<>();
        BigDecimal prevMarketCapitalization = BigDecimal.ZERO;
        for (SectorPriceListResponse.StockItem item : selectedByStock.values()) {
            String stockCode = StockCode.removeSuffix(item.stkCd());
            StockInfo stockInfo = stockInfoCache.get(stockCode);
            // API 응답 품질 문제로 필수 필드가 없는 종목 제외
            if (stockInfo == null || stockInfo.getLastPrice() == null || stockInfo.getListCount() == null) {
                continue;
            }
            // ELW, ETF 등 주권 외 종목 제외
            if (!StockMarketCode.isOrdinaryShare(stockInfo.getMarketCode())) {
                continue;
            }
            // 전일종가 × 상장주식수 = 종목별 전일 시가총액 합산
            prevMarketCapitalization = prevMarketCapitalization.add(
                    stockInfo.getLastPrice().multiply(BigDecimal.valueOf(stockInfo.getListCount())));
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

    private record StockCandidate(
            String stockCode,
            String stockName,
            String marketCode,
            BigDecimal curPrice,
            BigDecimal prevPrice,
            BigDecimal listCount) {}

    private record ScoredStock(
            String stockCode, String stockName, String marketCode, BigDecimal contribution, BigDecimal changeRate) {}

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
}
