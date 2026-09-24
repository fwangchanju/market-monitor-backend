package dev.eolmae.marketmonitor.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketValueTierThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketValueTierThresholdRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.CategoryTierAggregationService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketMapExcludedStockRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceCacheService.CachedStockPrice;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateMarketRanking;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryRankingSummary;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.dto.TopCategoryItem;
import dev.eolmae.marketmonitor.domain.view.enums.AverageMode;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MarketMapQueryServiceTest {

    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository =
            Mockito.mock(SectorPriceSnapshotRepository.class);
    private final SectorPriceCacheService sectorPriceCacheService = Mockito.mock(SectorPriceCacheService.class);
    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository =
            Mockito.mock(MarketMapExcludedStockRepository.class);
    private final MarketMapCategoryRepository marketMapCategoryRepository =
            Mockito.mock(MarketMapCategoryRepository.class);
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository =
            Mockito.mock(MarketMapStockCategoryRepository.class);
    // 구간 리포지토리 하나를 합산 클래스·구간 서비스가 같이 본다 — 트리 기반 랭킹은 이 둘이 같은 구간
    // 목록을 보는 것을 전제로 한다(합산 클래스는 findAll()로 라벨→id, 구간 서비스는
    // findAllByOrderByThresholdValueAsc()로 종목의 구간을 정한다).
    private final MarketValueTierThresholdRepository marketValueTierThresholdRepository =
            Mockito.mock(MarketValueTierThresholdRepository.class);
    private final CategoryTierAggregationService categoryTierAggregationService =
            new CategoryTierAggregationService(marketValueTierThresholdRepository);
    // mock 대신 진짜 객체를 쓴다 — resolveTier가 실제로 실행돼야 트리 기반 테스트의 종목이 의도한 구간에
    // 들어간다(5-1).
    private final MarketValueTierThresholdService marketValueTierThresholdService =
            new MarketValueTierThresholdService(marketValueTierThresholdRepository);
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository =
            Mockito.mock(MarketOverviewSnapshotRepository.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService =
            new SectorPriceSnapshotService(sectorPriceSnapshotRepository);
    private final MarketMapQueryService service = new MarketMapQueryService(
            stockInfoCacheService,
            sectorPriceSnapshotService,
            sectorPriceCacheService,
            marketMapExcludedStockRepository,
            marketMapCategoryRepository,
            marketMapStockCategoryRepository,
            categoryTierAggregationService,
            marketValueTierThresholdService,
            marketOverviewSnapshotRepository);

    // 구간 스텁 공통 셋업 — 진짜 구간 서비스로 바뀌면서 트리를 빌드하는 모든 테스트에 구간이 필요해졌다
    // (5-1). 구간이 여럿 필요한 테스트는 이 기본값을 자기 stubTierThresholds 호출로 덮어쓴다.
    @BeforeEach
    void stubDefaultTier() {
        stubTierThresholds(tierThreshold(10L, "대형", 0L, false));
    }

    @Test
    void getCustomMarketMap_트리집계와_카테고리명_매칭이_정확히_반영된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        MarketMapCategory electronics = category(1L, null, "전기/전자");
        MarketMapCategory semiconductor = category(2L, 1L, "반도체");
        MarketMapCategory chemical = category(3L, null, "화학");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(electronics, semiconductor, chemical));

        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(
                        MarketMapStockCategory.create("005930", 2L),
                        MarketMapStockCategory.create("000660", 2L),
                        MarketMapStockCategory.create("009150", 1L),
                        MarketMapStockCategory.create("051910", 3L)));

        StockInfo samsung = stockInfo("005930", "삼성전자", null, 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", null, 50L, BigDecimal.valueOf(20));
        StockInfo lgElectronics = stockInfo("009150", "삼성전기", null, 200L, BigDecimal.valueOf(5));
        StockInfo lgChem = stockInfo("051910", "LG화학", "화학", 500L, BigDecimal.ONE);

        Map<String, StockInfo> stockInfoCache = List.of(samsung, skHynix, lgElectronics, lgChem).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20)),
                        priceSnapshot("009150", snapshotTime, BigDecimal.valueOf(5)),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.snapshotTime()).isEqualTo(snapshotTime);
        List<MarketMapCategoryNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapCategoryNode electronicsNode = nodes.stream()
                .filter(node -> node.categoryName().equals("전기/전자"))
                .findFirst()
                .orElseThrow();
        assertThat(electronicsNode.items()).extracting("stockCode").containsExactly("009150");
        assertThat(electronicsNode.children()).hasSize(1);

        MarketMapCategoryNode semiconductorNode = electronicsNode.children().get(0);
        assertThat(semiconductorNode.categoryName()).isEqualTo("반도체");
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactlyInAnyOrder("005930", "000660");
        assertThat(semiconductorNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        // 전기/전자 총액 = 직속(009150: 5*200=1000) + 자식(반도체: 2000) = 3000
        assertThat(electronicsNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(3000));

        MarketMapCategoryNode chemicalNode = nodes.stream()
                .filter(node -> node.categoryName().equals("화학"))
                .findFirst()
                .orElseThrow();
        assertThat(chemicalNode.children()).isEmpty();
        assertThat(chemicalNode.items()).extracting("stockCode").containsExactly("051910");
        assertThat(chemicalNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void getDefaultMarketMap_stock_info_카테고리_그대로_1뎁스_노드로_묶인다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        // 기본 마켓맵은 market_map_category를 아예 안 쓰므로, 매칭되는 카테고리가 없어도(예: 종목 업종이
        // 나중에 바뀌어 자동생성된 카테고리가 없는 경우) 조회 자체가 깨지면 안 됨을 검증
        StockInfo samsung = stockInfo("005930", "삼성전자", "반도체", 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", "반도체", 50L, BigDecimal.valueOf(20));
        StockInfo lgChem = stockInfo("051910", "LG화학", "", 500L, BigDecimal.ONE);

        Map<String, StockInfo> stockInfoCache = List.of(samsung, skHynix, lgChem).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20)),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getDefaultMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.snapshotTime()).isEqualTo(snapshotTime);
        List<MarketMapCategoryNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapCategoryNode semiconductorNode = nodes.stream()
                .filter(node -> node.categoryName().equals("반도체"))
                .findFirst()
                .orElseThrow();
        assertThat(semiconductorNode.children()).isEmpty();
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactlyInAnyOrder("005930", "000660");
        assertThat(semiconductorNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(semiconductorNode.categoryId()).isEqualTo(0L);
        assertThat(semiconductorNode.isExcluded()).isFalse();

        MarketMapCategoryNode uncategorizedNode = nodes.stream()
                .filter(node -> node.categoryName().equals("미분류"))
                .findFirst()
                .orElseThrow();
        assertThat(uncategorizedNode.children()).isEmpty();
        assertThat(uncategorizedNode.items()).extracting("stockCode").containsExactly("051910");
        assertThat(uncategorizedNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void getCustomMarketMap_제외되었거나_빈_카테고리도_필터링없이_그대로_응답된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        MarketMapCategory semiconductor = category(1L, null, "반도체");
        MarketMapCategory empty = category(2L, null, "빈카테고리");
        empty.exclude();
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, empty));
        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 1L)));

        StockInfo samsung = stockInfo("005930", "삼성전자", null, 100L, BigDecimal.TEN);
        Map<String, StockInfo> stockInfoCache =
                List.of(samsung).stream().collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        List<MarketMapCategoryNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapCategoryNode semiconductorNode = nodes.stream()
                .filter(node -> node.categoryName().equals("반도체"))
                .findFirst()
                .orElseThrow();
        assertThat(semiconductorNode.categoryId()).isEqualTo(1L);
        assertThat(semiconductorNode.isExcluded()).isFalse();

        MarketMapCategoryNode emptyNode = nodes.stream()
                .filter(node -> node.categoryName().equals("빈카테고리"))
                .findFirst()
                .orElseThrow();
        assertThat(emptyNode.categoryId()).isEqualTo(2L);
        assertThat(emptyNode.isExcluded()).isTrue();
        assertThat(emptyNode.items()).isEmpty();
        assertThat(emptyNode.children()).isEmpty();
    }

    @Test
    void getCustomMarketMap_자식_카테고리가_있는_노드의_총액은_자기_items와_자식_합계다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        MarketMapCategory parent = category(1L, null, "전기/전자");
        MarketMapCategory child = category(2L, 1L, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parent, child));
        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(
                        MarketMapStockCategory.create("009150", 1L), MarketMapStockCategory.create("005930", 2L)));

        StockInfo lgElectronics = stockInfo("009150", "삼성전기", null, 200L, BigDecimal.valueOf(5));
        StockInfo samsung = stockInfo("005930", "삼성전자", null, 100L, BigDecimal.TEN);
        Map<String, StockInfo> stockInfoCache = List.of(lgElectronics, samsung).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(
                        priceSnapshot("009150", snapshotTime, BigDecimal.valueOf(5)),
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        MarketMapCategoryNode parentNode = response.items().stream()
                .filter(node -> node.categoryName().equals("전기/전자"))
                .findFirst()
                .orElseThrow();
        // 직속(009150: 5*200=1000) + 자식(반도체: 10*100=1000) = 2000
        assertThat(parentNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));
    }

    @Test
    void getCustomMarketMap_가격_스냅샷이_없는_종목은_제외된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        MarketMapCategory semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(
                        MarketMapStockCategory.create("005930", 1L), MarketMapStockCategory.create("000660", 1L)));

        StockInfo samsung = stockInfo("005930", "삼성전자", null, 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", null, 50L, BigDecimal.valueOf(20));
        Map<String, StockInfo> stockInfoCache = List.of(samsung, skHynix).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        // 000660은 가격 스냅샷이 없다 — 수집 gap 등으로 그 시각에 데이터가 아예 없는 경우
        when(sectorPriceCacheService.getCache(Market.KOSPI, snapshotTime))
                .thenReturn(Map.ofEntries(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        MarketMapCategoryNode semiconductorNode = response.items().stream()
                .filter(node -> node.categoryName().equals("반도체"))
                .findFirst()
                .orElseThrow();
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactly("005930");
    }

    @Test
    void getCustomMarketMap_카테고리가_하나도_없으면_빈_리스트를_반환한다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        when(marketMapCategoryRepository.findAll()).thenReturn(List.of());
        when(marketMapStockCategoryRepository.findAll()).thenReturn(List.of());
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        // 가격 캐시는 스텁하지 않는다 — 스텁 없는 mock의 getCache는 기본값(빈 맵)을 그대로 돌려준다.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void getCustomMarketMap_그_시각에_지수_스냅샷이_있으면_marketOverview로_붙는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        when(marketMapCategoryRepository.findAll()).thenReturn(List.of());
        when(marketMapStockCategoryRepository.findAll()).thenReturn(List.of());
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23))));

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, null);

        assertThat(response.marketOverview()).isNotNull();
        assertThat(response.marketOverview().market()).isEqualTo(Market.KOSPI);
        assertThat(response.marketOverview().changeRate()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
    }

    @Test
    void getCustomMarketMap_마켓이_여럿이면_단일_지수값이_없어_marketOverview가_null이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        List<Market> markets = List.of(Market.KOSPI, Market.KOSDAQ);

        when(marketMapCategoryRepository.findAll()).thenReturn(List.of());
        when(marketMapStockCategoryRepository.findAll()).thenReturn(List.of());
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(markets))
                .thenReturn(Optional.of(snapshotTime));

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.ALL_STOCK, null);

        // 마켓이 여럿이면 합쳐서 보여줄 단일 지수값이 없으므로, 지수 스냅샷 자체를 조회하지 않고 곧장 null.
        assertThat(response.marketOverview()).isNull();
        Mockito.verifyNoInteractions(marketOverviewSnapshotRepository);
    }

    @Test
    void getCustomMarketMap_snapshotTime을_명시하면_최신이_아니라_그_시각_그대로_쓴다() {
        LocalDateTime requestedTime = LocalDateTime.of(2026, 7, 31, 10, 5);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of());
        when(marketMapStockCategoryRepository.findAll()).thenReturn(List.of());
        when(stockInfoCacheService.getCache()).thenReturn(Map.of());
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSPI, requestedTime))
                .thenReturn(true);
        when(marketOverviewSnapshotRepository.findBySnapshotTime(requestedTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI, requestedTime);

        assertThat(response.snapshotTime()).isEqualTo(requestedTime);
        // 명시한 시각이 있으면 "최신 공통 시각" 조회는 부르지 않는다 — 가까운 시각으로 대체하지 않는다.
        Mockito.verify(sectorPriceSnapshotRepository, Mockito.never()).findLatestCommonSnapshotTime(Mockito.anyList());
    }

    // ALL_STOCK인데 그 시각에 코스피만 있고 코스닥은 없는 경우 — 반쪽 트리를 내려주면 안 된다(결정 4).
    @Test
    void getCustomMarketMap_snapshotTime에_요청_마켓_중_하나라도_없으면_빈_응답이다() {
        LocalDateTime requestedTime = LocalDateTime.of(2026, 7, 31, 10, 5);
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSPI, requestedTime))
                .thenReturn(true);
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(Market.KOSDAQ, requestedTime))
                .thenReturn(false);

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.ALL_STOCK, requestedTime);

        assertThat(response).isEqualTo(MarketMapResponse.empty());
    }

    @Test
    void getCategoryChangeRates_랭킹과_같은_시각의_지수_등락률이_마켓별로_붙는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        MarketMapCategory pharma = category(2L, null, "제약");
        stubCategoryTree(
                List.of(semiconductor, pharma),
                List.of(MarketMapStockCategory.create("A", 1L), MarketMapStockCategory.create("B", 2L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSDAQ));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        stubPrices(
                Market.KOSDAQ,
                snapshotTime,
                price("B", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(
                        marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23)),
                        marketOverviewSnapshot(Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(-0.45))));
        // beforeMinutes=60이므로 09:00 시각도 별도로 조회한다 — now/before 둘 다 값이 있는 일반적인 경우.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime.minusMinutes(60)))
                .thenReturn(List.of(
                        marketOverviewSnapshot(Market.KOSPI, snapshotTime.minusMinutes(60), BigDecimal.valueOf(0.98)),
                        marketOverviewSnapshot(
                                Market.KOSDAQ, snapshotTime.minusMinutes(60), BigDecimal.valueOf(-0.20))));

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.ALL_STOCK, snapshotTime, 60);

        CategoryChangeRateMarketRanking kospi = response.items().stream()
                .filter(ranking -> ranking.market() == Market.KOSPI)
                .findFirst()
                .orElseThrow();
        assertThat(kospi.index().now()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
        assertThat(kospi.index().before()).isEqualByComparingTo(BigDecimal.valueOf(0.98));

        CategoryChangeRateMarketRanking kosdaq = response.items().stream()
                .filter(ranking -> ranking.market() == Market.KOSDAQ)
                .findFirst()
                .orElseThrow();
        assertThat(kosdaq.index().now()).isEqualByComparingTo(BigDecimal.valueOf(-0.45));
        assertThat(kosdaq.index().before()).isEqualByComparingTo(BigDecimal.valueOf(-0.20));
    }

    @Test
    void getCategoryChangeRates_그_시각에_지수_스냅샷이_없으면_index가_null이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        stubCategoryTree(List.of(semiconductor), List.of(MarketMapStockCategory.create("A", 1L)));
        stubStockCache(stock("A", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        // 이번 수집 주기에 지수기여도랭킹 수집만 실패해서, 카테고리 랭킹은 있는데 지수 스냅샷은 그 시각에
        // 없는 경우 — 다른 시각 값으로 조용히 대체하지 않고 index 전체가 null로 내려간다.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.KOSPI, snapshotTime, 60);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).index()).isNull();
    }

    @Test
    void getCategoryChangeRates_before_시각에_지수_스냅샷이_없으면_index_before가_null이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        stubCategoryTree(List.of(semiconductor), List.of(MarketMapStockCategory.create("A", 1L)));
        stubStockCache(stock("A", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        // now 시각(10:00)에는 지수 스냅샷이 있지만, before 시각(09:00, beforeMinutes=60)에는 없는
        // 경우 — 장 시작 직후나 수집 gap. 가까운 다른 시점 값으로 대체하지 않고 before만 null이다.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23))));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime.minusMinutes(60)))
                .thenReturn(List.of());

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.KOSPI, snapshotTime, 60);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).index().now()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
        assertThat(response.items().get(0).index().before()).isNull();
    }

    @Test
    void getTopCategoryRankings_자식_카테고리는_랭킹에서_제외된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        MarketMapCategory root = category(1L, null, "반도체");
        MarketMapCategory child = category(2L, 1L, "반도체 소재");
        stubCategoryTree(
                List.of(root, child),
                List.of(MarketMapStockCategory.create("A1", 1L), MarketMapStockCategory.create("A2", 2L)));
        stubStockCache(stock("A1", Market.KOSPI), stock("A2", Market.KOSPI));
        // child(A2)가 root보다 등락률이 훨씬 높아도(부모 집계에 재귀로 포함되긴 하지만), 대분류가
        // 아니므로 결과에 별도로 나오면 안 된다.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)),
                price("A2", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(90)));
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("A1", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO),
                price("A2", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO));

        List<CategoryRankingSummary> summaries =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("반도체");
    }

    @Test
    void getTopCategoryRankings_TOP2까지만_등락률_내림차순으로_노출된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        MarketMapCategory c = category(3L, null, "자동차");
        stubCategoryTree(
                List.of(a, b, c),
                List.of(
                        MarketMapStockCategory.create("A", 1L),
                        MarketMapStockCategory.create("B", 2L),
                        MarketMapStockCategory.create("C", 3L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSPI), stock("C", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN), // +10%p
                price("B", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)), // +5%p
                price(
                        "C",
                        Market.KOSPI,
                        snapshotTime,
                        BigDecimal.valueOf(10_000),
                        BigDecimal.valueOf(2))); // +2%p, 3위라 빠져야 함
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("A", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO),
                price("B", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO),
                price("C", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO));

        List<CategoryRankingSummary> summaries =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("반도체", "화학");
    }

    // before가 없는 것을 0으로 치면 now가 그대로 델타가 되어, 실제로는 계산할 수 없는 카테고리가
    // 1위로 올라온다. 순위에서 빼는 것이 맞다.
    @Test
    void getTopCategoryRankings_before가_없는_카테고리는_랭킹에서_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        stubCategoryTree(
                List.of(a, b), List.of(MarketMapStockCategory.create("A", 1L), MarketMapStockCategory.create("B", 2L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(90)),
                price("B", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)));
        // before: A는 가격 행 자체가 없다(before 없음), B만 0%로 존재한다.
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("B", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO));

        List<CategoryRankingSummary> summaries =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("화학");
    }

    // 그 시각 스냅샷이 통째로 없는 경우 — 매일 첫 발송(08:10의 before는 07:55인데 수집은 08:00부터)이
    // 여기 걸린다. 캡션 쪽에서 안내 문구로 바꿔 내보낸다.
    @Test
    void getTopCategoryRankings_before가_전부_없으면_빈_목록이_된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        stubCategoryTree(List.of(a), List.of(MarketMapStockCategory.create("A", 1L)));
        stubStockCache(stock("A", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        // before 시각은 존재 자체를 스텁하지 않는다 — 그 시각 스냅샷이 통째로 없는 경우.

        List<CategoryRankingSummary> summaries =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topCategories()).isEmpty();
    }

    @Test
    void getTopCategoryRankings_기본_제외_구간은_평균_계산에서_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        MarketMapCategory root = category(1L, null, "반도체");
        // 구간은 시가총액으로 정해진다 — 대형 1종목(시총 10,000, +10%)과 소형 4종목(합계 시총 10,000,
        // 각 -50%)으로 나눈다. 소형은 기본 제외 구간이라 평균에서 빠지면 +10%만 남는다.
        stubTierThresholds(tierThreshold(20L, "소형", 0L, true), tierThreshold(10L, "대형", 5_000L, false));
        stubCategoryTree(
                List.of(root),
                List.of(
                        MarketMapStockCategory.create("BIG", 1L),
                        MarketMapStockCategory.create("S1", 1L),
                        MarketMapStockCategory.create("S2", 1L),
                        MarketMapStockCategory.create("S3", 1L),
                        MarketMapStockCategory.create("S4", 1L)));
        stubStockCache(
                stock("BIG", Market.KOSPI),
                stock("S1", Market.KOSPI),
                stock("S2", Market.KOSPI),
                stock("S3", Market.KOSPI),
                stock("S4", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("BIG", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN),
                price("S1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(2_500), BigDecimal.valueOf(-50)),
                price("S2", Market.KOSPI, snapshotTime, BigDecimal.valueOf(2_500), BigDecimal.valueOf(-50)),
                price("S3", Market.KOSPI, snapshotTime, BigDecimal.valueOf(2_500), BigDecimal.valueOf(-50)),
                price("S4", Market.KOSPI, snapshotTime, BigDecimal.valueOf(2_500), BigDecimal.valueOf(-50)));
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("BIG", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.ZERO),
                price("S1", Market.KOSPI, beforeTime, BigDecimal.valueOf(2_500), BigDecimal.ZERO),
                price("S2", Market.KOSPI, beforeTime, BigDecimal.valueOf(2_500), BigDecimal.ZERO),
                price("S3", Market.KOSPI, beforeTime, BigDecimal.valueOf(2_500), BigDecimal.ZERO),
                price("S4", Market.KOSPI, beforeTime, BigDecimal.valueOf(2_500), BigDecimal.ZERO));

        List<CategoryRankingSummary> summaries =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topCategories().get(0).changeRate()).isEqualByComparingTo(BigDecimal.TEN);
    }

    // 델타(now-before) 기준과 등락률(now) 기준이 서로 다른 카테고리를 뽑을 수 있음을 보인다 — 08:10
    // 폴백이 델타 대신 이 랭킹을 쓰는 이유다.
    @Test
    void getTopCategoryRankingsByChangeRate_등락률_기준으로_델타_기준과_다른_카테고리를_뽑는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        MarketMapCategory c = category(3L, null, "자동차");
        stubCategoryTree(
                List.of(a, b, c),
                List.of(
                        MarketMapStockCategory.create("A", 1L),
                        MarketMapStockCategory.create("B", 2L),
                        MarketMapStockCategory.create("C", 3L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSPI), stock("C", Market.KOSPI));
        // a: now +20%, before +19% → 델타 +1%p. b: now +5%, before -10% → 델타 +15%p.
        // c: now +10%, before +8% → 델타 +2%p.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(20)),
                price("B", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)),
                price("C", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("A", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(19)),
                price("B", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(-10)),
                price("C", Market.KOSPI, beforeTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(8)));

        List<CategoryRankingSummary> deltaRankings =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);
        List<CategoryRankingSummary> changeRateRankings = service.getTopCategoryRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(deltaRankings.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("화학", "자동차");
        assertThat(changeRateRankings.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("반도체", "자동차");
    }

    @Test
    void getMergedTopCategoryRanking_두_마켓의_원시값을_합친_기준으로_TOP2를_뽑는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        MarketMapCategory c = category(3L, null, "자동차");
        stubCategoryTree(
                List.of(a, b, c),
                List.of(
                        MarketMapStockCategory.create("A1", 1L),
                        MarketMapStockCategory.create("A2", 1L),
                        MarketMapStockCategory.create("B1", 2L),
                        MarketMapStockCategory.create("B2", 2L),
                        MarketMapStockCategory.create("C1", 3L)));
        stubStockCache(
                stock("A1", Market.KOSPI),
                stock("A2", Market.KOSDAQ),
                stock("B1", Market.KOSPI),
                stock("B2", Market.KOSDAQ),
                stock("C1", Market.KOSPI));
        // a는 KOSPI 단독으로 보면 압도적 1위(+100%)지만 KOSDAQ에서 -90%라 합치면 +5%로 밀린다.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(100)),
                price("B1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(8)),
                price("C1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(6)));
        stubPrices(
                Market.KOSDAQ,
                snapshotTime,
                price("A2", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(-90)),
                price("B2", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(8)));

        List<TopCategoryItem> merged =
                service.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false);

        // 병합 평균: a=(1,000,000-900,000)/20,000=+5%, b=(80,000+80,000)/20,000=+8%, c=60,000/10,000=+6%
        assertThat(merged).extracting(TopCategoryItem::categoryName).containsExactly("화학", "자동차");
    }

    @Test
    void getMergedTopCategoryRanking_원시값을_합산한_뒤_한_번만_나눈다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        stubCategoryTree(
                List.of(a), List.of(MarketMapStockCategory.create("A1", 1L), MarketMapStockCategory.create("A2", 1L)));
        stubStockCache(stock("A1", Market.KOSPI), stock("A2", Market.KOSDAQ));
        // KOSPI 시총 10,000에 +10%p, KOSDAQ 시총 40,000에 -2%p — 단순 평균이면 (10-2)/2=+4가 되지만,
        // KOSDAQ 쪽 시총 비중이 훨씬 커서 원시값을 합산한 뒤 나누면 +0.4가 맞다.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        stubPrices(
                Market.KOSDAQ,
                snapshotTime,
                price("A2", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(40_000), BigDecimal.valueOf(-2)));

        List<TopCategoryItem> merged =
                service.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false);

        assertThat(merged.get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(0.4));
    }

    // 등락률 수집만 실패한 tick이 이 모양이다 — 맵 페이지는 sector_price_snapshot으로 그려져서 멀쩡히
    // 나오는데 여기는 빈 목록이 된다. 호출부(MarketMapAlbumReportSender)가 이걸 "캡처할 마켓이 없다"로
    // 읽으면 한 장도 안 찍고 에스컬레이션하므로, 빈 목록이 정상 반환이라는 것을 못박아둔다.
    @Test
    void getMergedTopCategoryRanking_그_시각_스냅샷이_없으면_빈_목록이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        stubCategoryTree(List.of(category(1L, null, "반도체")), List.of());
        // KOSPI·KOSDAQ 둘 다 그 시각 스냅샷 자체를 스텁하지 않는다 — 캡처가 아예 안 된 tick.

        assertThat(service.getMergedTopCategoryRanking(
                        MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false))
                .isEmpty();
    }

    // 결정 2가 새로 더한 규칙 — 한 마켓만 데이터가 없어도(다른 마켓은 멀쩡해도) 전체가 빈 목록이어야
    // 한다. 위 테스트(둘 다 없음)만으로는 "하나라도"인지 "전부"인지 구분이 안 된다.
    @Test
    void getMergedTopCategoryRanking_한_마켓만_합산이_비어도_빈_목록이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        stubCategoryTree(List.of(a), List.of(MarketMapStockCategory.create("A1", 1L)));
        stubStockCache(stock("A1", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        // KOSDAQ은 그 시각 스냅샷 자체가 없다 — KOSPI만 있어도 전체가 빈 목록이어야 한다.

        assertThat(service.getMergedTopCategoryRanking(
                        MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false))
                .isEmpty();
    }

    // 맵 앨범 캡션(getMergedTopCategoryRanking)에서도 섹터 제외 on/off로 TOP2가 달라진다 — 지시서 결정
    // 3이 고치는 두 캡션 경로(섹터/맵) 중 나머지 하나.
    @Test
    void getMergedTopCategoryRanking_섹터_제외를_켜면_isExcluded_카테고리가_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory excluded = category(1L, null, "반도체");
        excluded.exclude();
        MarketMapCategory included = category(2L, null, "화학");
        stubCategoryTree(
                List.of(excluded, included),
                List.of(
                        MarketMapStockCategory.create("A1", 1L),
                        MarketMapStockCategory.create("B1", 2L),
                        MarketMapStockCategory.create("B2", 2L)));
        stubStockCache(stock("A1", Market.KOSPI), stock("B1", Market.KOSPI), stock("B2", Market.KOSDAQ));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(90)),
                price("B1", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)));
        // KOSDAQ에 아무 종목도 없으면 "하나라도 비면 빈 목록" 규칙에 걸려 전체가 빈다 — 화학에 KOSDAQ
        // 종목을 하나 더한다. 등락률이 같아(+5%) 병합 뒤 값은 그대로다.
        stubPrices(
                Market.KOSDAQ,
                snapshotTime,
                price("B2", Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)));

        List<TopCategoryItem> filtered =
                service.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, true);
        List<TopCategoryItem> unfiltered =
                service.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false);

        assertThat(filtered).extracting(TopCategoryItem::categoryName).containsExactly("화학");
        assertThat(unfiltered).extracting(TopCategoryItem::categoryName).containsExactlyInAnyOrder("반도체", "화학");
    }

    // 가중평균과 산술평균이 실제로 다른 값이 나오는 것을 보여준다 — 종목 하나짜리 픽스처는 itemCount가
    // 항상 1이라 두 평균이 우연히 같아서 이 분기를 검증하지 못한다.
    @Test
    void getTopCategoryRankingsByChangeRate_평균_방식에_따라_결과가_달라진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        stubCategoryTree(
                List.of(a),
                List.of(MarketMapStockCategory.create("BIG", 1L), MarketMapStockCategory.create("SMALL", 1L)));
        stubStockCache(stock("BIG", Market.KOSPI), stock("SMALL", Market.KOSPI));
        // 시총 90,000짜리 종목 +30%, 시총 10,000짜리 종목 +10% — 가중평균은 시총이 큰 쪽에 끌려 +28%,
        // 산술평균은 종목당 등락률을 그대로 평균내 +20%.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("BIG", Market.KOSPI, snapshotTime, BigDecimal.valueOf(90_000), BigDecimal.valueOf(30)),
                price("SMALL", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));

        List<CategoryRankingSummary> weighted = service.getTopCategoryRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);
        List<CategoryRankingSummary> simple = service.getTopCategoryRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.SIMPLE, false);

        assertThat(weighted.get(0).topCategories().get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(28));
        assertThat(simple.get(0).topCategories().get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    // 평상시(매 tick) 경로 — getTopCategoryRankings → toTopCategoryItem은 avgOf를 now·before 두 번
    // 불러 그 차이를 쓴다. ByChangeRate 폴백 경로만 덮으면 두 호출 중 하나가 다른 모드를 써도 못 잡는다.
    @Test
    void getTopCategoryRankings_평균_방식에_따라_델타_결과가_달라진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime beforeTime = snapshotTime.minusMinutes(60);
        MarketMapCategory a = category(1L, null, "반도체");
        stubCategoryTree(
                List.of(a),
                List.of(MarketMapStockCategory.create("BIG", 1L), MarketMapStockCategory.create("SMALL", 1L)));
        stubStockCache(stock("BIG", Market.KOSPI), stock("SMALL", Market.KOSPI));
        // now:    시총 90,000 +30% / 10,000 +10%  → 가중 +28%, 산술 +20%
        // before: 시총 80,000 +10% / 20,000   0%  → 가중  +8%, 산술  +5%
        // → 가중 델타는 +20%p, 산술 델타는 +15%p로 서로 다르다.
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("BIG", Market.KOSPI, snapshotTime, BigDecimal.valueOf(90_000), BigDecimal.valueOf(30)),
                price("SMALL", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        stubPrices(
                Market.KOSPI,
                beforeTime,
                price("BIG", Market.KOSPI, beforeTime, BigDecimal.valueOf(80_000), BigDecimal.TEN),
                price("SMALL", Market.KOSPI, beforeTime, BigDecimal.valueOf(20_000), BigDecimal.ZERO));

        List<CategoryRankingSummary> weighted =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);
        List<CategoryRankingSummary> simple =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.SIMPLE, false);

        assertThat(weighted.get(0).topCategories().get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(simple.get(0).topCategories().get(0).changeRate()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }

    // 섹터 제외(market_map_category.is_excluded)를 켜고 끄면 TOP2에 들어오는 카테고리가 달라진다.
    @Test
    void getTopCategoryRankingsByChangeRate_섹터_제외를_켜면_isExcluded_카테고리가_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory excluded = category(1L, null, "반도체");
        excluded.exclude();
        MarketMapCategory included = category(2L, null, "화학");
        stubCategoryTree(
                List.of(excluded, included),
                List.of(MarketMapStockCategory.create("A", 1L), MarketMapStockCategory.create("B", 2L)));
        stubStockCache(stock("A", Market.KOSPI), stock("B", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(90)),
                price("B", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.valueOf(5)));

        List<CategoryRankingSummary> filtered = service.getTopCategoryRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, true);
        List<CategoryRankingSummary> unfiltered = service.getTopCategoryRankingsByChangeRate(
                MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(filtered.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("화학");
        assertThat(unfiltered.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("반도체", "화학");
    }

    // 텔레그램 섹터 캡션의 "#코스피 +x.xx%"가 CategoryRankingSummary.indexChangeRate를 읽는다
    // (toCategoryRankingSummary의 marketRanking.index().now()) — 캡션이 실제로 읽는 자리에 지수 값이
    // 실리는지 보는 테스트가 지금까지 없었다.
    @Test
    void getTopCategoryRankings_지수_등락률이_함께_담긴다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        stubCategoryTree(List.of(a), List.of(MarketMapStockCategory.create("A", 1L)));
        stubStockCache(stock("A", Market.KOSPI));
        stubPrices(
                Market.KOSPI,
                snapshotTime,
                price("A", Market.KOSPI, snapshotTime, BigDecimal.valueOf(10_000), BigDecimal.TEN));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23))));

        List<CategoryRankingSummary> summaries =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).indexChangeRate()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
    }

    private void stubTierThresholds(MarketValueTierThreshold... tiers) {
        List<MarketValueTierThreshold> sorted = List.of(tiers).stream()
                .sorted(Comparator.comparing(MarketValueTierThreshold::getThresholdValue))
                .toList();
        when(marketValueTierThresholdRepository.findAll()).thenReturn(List.of(tiers));
        when(marketValueTierThresholdRepository.findAllByOrderByThresholdValueAsc())
                .thenReturn(sorted);
    }

    private MarketValueTierThreshold tierThreshold(
            Long id, String label, long thresholdValue, boolean excludedByDefault) {
        MarketValueTierThreshold threshold = MarketValueTierThreshold.create(label, thresholdValue, excludedByDefault);
        ReflectionTestUtils.setField(threshold, "id", id);
        return threshold;
    }

    private void stubCategoryTree(List<MarketMapCategory> categories, List<MarketMapStockCategory> assignments) {
        when(marketMapCategoryRepository.findAll()).thenReturn(categories);
        when(marketMapStockCategoryRepository.findAll()).thenReturn(assignments);
    }

    private void stubStockCache(StockInfo... stocks) {
        when(stockInfoCacheService.getCache())
                .thenReturn(List.of(stocks).stream()
                        .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity())));
    }

    @SafeVarargs
    private final void stubPrices(Market market, LocalDateTime time, Map.Entry<String, CachedStockPrice>... prices) {
        when(sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, time))
                .thenReturn(true);
        when(sectorPriceCacheService.getCache(market, time)).thenReturn(Map.ofEntries(prices));
    }

    /** 트리 기반 랭킹 테스트용 종목 — listCount를 1로 고정해 price()의 totalMarketValue를 그대로
     * 시가총액으로 쓴다(currentPrice * listCount = currentPrice). */
    private StockInfo stock(String stockCode, Market market) {
        return StockInfo.create(stockCode, stockCode, market, "0", null, 1L, BigDecimal.TEN);
    }

    /** market은 stubPrices가 이미 (market, time) 단위로 캐시를 스텁하는 키라 여기서는 안 쓴다 —
     * 호출부를 그대로 두기 위해 시그니처만 유지한다. */
    private Map.Entry<String, CachedStockPrice> price(
            String stockCode, Market market, LocalDateTime time, BigDecimal totalMarketValue, BigDecimal changeRate) {
        return Map.entry(stockCode, new CachedStockPrice(totalMarketValue, changeRate, time));
    }

    private MarketOverviewSnapshot marketOverviewSnapshot(
            Market market, LocalDateTime snapshotTime, BigDecimal changeRate) {
        return MarketOverviewSnapshot.create(
                market,
                snapshotTime,
                BigDecimal.valueOf(2500),
                BigDecimal.ONE,
                changeRate,
                BigDecimal.ZERO,
                "OPEN",
                0,
                0,
                0,
                0,
                0,
                snapshotTime);
    }

    private MarketMapCategory category(Long id, Long parentId, String name) {
        MarketMapCategory category =
                parentId == null ? MarketMapCategory.createParent(name) : categoryWithParent(parentId, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private MarketMapCategory categoryWithParent(Long parentId, String name) {
        MarketMapCategory parent = MarketMapCategory.createParent("parent-placeholder");
        ReflectionTestUtils.setField(parent, "id", parentId);
        return MarketMapCategory.createChild(name, parent);
    }

    private StockInfo stockInfo(
            String stockCode, String stockName, String categoryName, Long listCount, BigDecimal lastPrice) {
        return StockInfo.create(stockCode, stockName, Market.KOSPI, "0", categoryName, listCount, lastPrice);
    }

    private Map.Entry<String, CachedStockPrice> priceSnapshot(
            String stockCode, LocalDateTime snapshotTime, BigDecimal currentPrice) {
        return Map.entry(stockCode, new CachedStockPrice(currentPrice, BigDecimal.ZERO, snapshotTime));
    }
}
