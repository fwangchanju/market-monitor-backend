package dev.eolmae.marketmonitor.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketValueTierItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryChangeRateSnapshotService;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.enums.ExchangeType;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketMapExcludedStockRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateItem;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryChangeRateMarketRanking;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryRankingSummary;
import dev.eolmae.marketmonitor.domain.view.dto.CategoryTierBreakdown;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketmonitor.domain.view.dto.TopCategoryItem;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MarketMapQueryServiceTest {

    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository =
            Mockito.mock(SectorPriceSnapshotRepository.class);
    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository =
            Mockito.mock(MarketMapExcludedStockRepository.class);
    private final MarketMapCategoryRepository marketMapCategoryRepository =
            Mockito.mock(MarketMapCategoryRepository.class);
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository =
            Mockito.mock(MarketMapStockCategoryRepository.class);
    private final MarketMapCategoryChangeRateSnapshotService marketMapCategoryChangeRateSnapshotService =
            Mockito.mock(MarketMapCategoryChangeRateSnapshotService.class);
    private final MarketValueTierThresholdService marketValueTierThresholdService =
            Mockito.mock(MarketValueTierThresholdService.class);
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository =
            Mockito.mock(MarketOverviewSnapshotRepository.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService =
            new SectorPriceSnapshotService(sectorPriceSnapshotRepository);
    private final MarketMapQueryService service = new MarketMapQueryService(
            stockInfoCacheService,
            sectorPriceSnapshotService,
            marketMapExcludedStockRepository,
            marketMapCategoryRepository,
            marketMapStockCategoryRepository,
            marketMapCategoryChangeRateSnapshotService,
            marketValueTierThresholdService,
            marketOverviewSnapshotRepository);

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
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20)),
                        priceSnapshot("009150", snapshotTime, BigDecimal.valueOf(5)),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI);

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
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20)),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getDefaultMarketMap(MarketQuery.KOSPI);

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
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI);

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
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of(
                        priceSnapshot("009150", snapshotTime, BigDecimal.valueOf(5)),
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI);

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
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI);

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
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of());
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI);

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
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(List.of(Market.KOSPI), snapshotTime))
                .thenReturn(List.of());
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23))));

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.KOSPI);

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
        when(sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(markets, snapshotTime))
                .thenReturn(List.of());

        MarketMapResponse response = service.getCustomMarketMap(MarketQuery.ALL_STOCK);

        // 마켓이 여럿이면 합쳐서 보여줄 단일 지수값이 없으므로, 지수 스냅샷 자체를 조회하지 않고 곧장 null.
        assertThat(response.marketOverview()).isNull();
        Mockito.verifyNoInteractions(marketOverviewSnapshotRepository);
    }

    @Test
    void getCategoryChangeRates_랭킹_스냅샷이_없으면_빈_응답을_그대로_반환한다() {
        when(marketMapCategoryChangeRateSnapshotService.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.empty());

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.KOSPI, 60);

        assertThat(response.snapshotTime()).isNull();
        assertThat(response.items()).isEmpty();
        Mockito.verifyNoInteractions(marketOverviewSnapshotRepository);
    }

    @Test
    void getCategoryChangeRates_랭킹과_같은_시각의_지수_등락률이_마켓별로_붙는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CategoryChangeRateMarketRanking kospiRanking = new CategoryChangeRateMarketRanking(
                Market.KOSPI, List.of(CategoryChangeRateItem.withoutBefore(1L, List.of())));
        CategoryChangeRateMarketRanking kosdaqRanking = new CategoryChangeRateMarketRanking(
                Market.KOSDAQ, List.of(CategoryChangeRateItem.withoutBefore(2L, List.of())));
        when(marketMapCategoryChangeRateSnapshotService.findLatestCommonSnapshotTime(
                        List.of(Market.KOSPI, Market.KOSDAQ)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(
                        List.of(Market.KOSPI, Market.KOSDAQ), snapshotTime, 60))
                .thenReturn(new SnapshotResponse<>(snapshotTime, List.of(kospiRanking, kosdaqRanking)));
        when(marketMapCategoryRepository.findAll())
                .thenReturn(List.of(category(1L, null, "반도체"), category(2L, null, "제약")));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(
                        marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23)),
                        marketOverviewSnapshot(Market.KOSDAQ, snapshotTime, BigDecimal.valueOf(-0.45))));

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.ALL_STOCK, 60);

        CategoryChangeRateMarketRanking kospi = response.items().stream()
                .filter(ranking -> ranking.market() == Market.KOSPI)
                .findFirst()
                .orElseThrow();
        assertThat(kospi.indexChangeRate()).isEqualByComparingTo(BigDecimal.valueOf(1.23));

        CategoryChangeRateMarketRanking kosdaq = response.items().stream()
                .filter(ranking -> ranking.market() == Market.KOSDAQ)
                .findFirst()
                .orElseThrow();
        assertThat(kosdaq.indexChangeRate()).isEqualByComparingTo(BigDecimal.valueOf(-0.45));
    }

    @Test
    void getCategoryChangeRates_그_시각에_지수_스냅샷이_없으면_indexChangeRate가_null이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CategoryChangeRateMarketRanking kospiRanking = new CategoryChangeRateMarketRanking(
                Market.KOSPI, List.of(CategoryChangeRateItem.withoutBefore(1L, List.of())));
        when(marketMapCategoryChangeRateSnapshotService.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(List.of(Market.KOSPI), snapshotTime, 60))
                .thenReturn(new SnapshotResponse<>(snapshotTime, List.of(kospiRanking)));
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(category(1L, null, "반도체")));
        // 이번 수집 주기에 지수기여도랭킹 수집만 실패해서, 카테고리 랭킹은 있는데 지수 스냅샷은 그 시각에
        // 없는 경우 — 다른 시각 값으로 조용히 대체하지 않고 null로 내려간다.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.KOSPI, 60);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).indexChangeRate()).isNull();
    }

    @Test
    void getCategoryChangeRates_스냅샷의_categoryId가_카테고리_테이블에_없으면_그_항목만_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        // 카테고리 버전 복원 직후처럼, 스냅샷 row는 이미 지워진 categoryId(99L)를 가리킬 수 있다.
        CategoryChangeRateMarketRanking kospiRanking = new CategoryChangeRateMarketRanking(
                Market.KOSPI,
                List.of(
                        CategoryChangeRateItem.withoutBefore(1L, List.of()),
                        CategoryChangeRateItem.withoutBefore(99L, List.of())));
        when(marketMapCategoryChangeRateSnapshotService.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(List.of(Market.KOSPI), snapshotTime, 60))
                .thenReturn(new SnapshotResponse<>(snapshotTime, List.of(kospiRanking)));
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(category(1L, null, "반도체")));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.KOSPI, 60);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).items()).extracting("categoryId").containsExactly(1L);
    }

    @Test
    void getTopCategoryRankings_자식_카테고리는_랭킹에서_제외된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory root = category(1L, null, "반도체");
        MarketMapCategory child = category(2L, 1L, "반도체 소재");
        // child가 root보다 등락률이 훨씬 높아도, 대분류가 아니므로 결과에 나오면 안 된다.
        stubRankingForTopCategories(
                snapshotTime,
                List.of(root, child),
                List.of(),
                changeRateItem(root.getId(), tier(10L, "대형", 50_000, 10000)), // +5%
                changeRateItem(child.getId(), tier(10L, "대형", 900_000, 10000))); // +90%

        List<CategoryRankingSummary> summaries = service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("반도체");
    }

    @Test
    void getTopCategoryRankings_TOP3까지만_등락률_내림차순으로_노출된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        MarketMapCategory c = category(3L, null, "자동차");
        MarketMapCategory d = category(4L, null, "철강");
        stubRankingForTopCategories(
                snapshotTime,
                List.of(a, b, c, d),
                List.of(),
                changeRateItem(a.getId(), tier(10L, "대형", 100_000, 10000)), // +10%
                changeRateItem(b.getId(), tier(10L, "대형", 50_000, 10000)), // +5%
                changeRateItem(c.getId(), tier(10L, "대형", 20_000, 10000)), // +2%
                changeRateItem(d.getId(), tier(10L, "대형", 10_000, 10000))); // +1%, 4위라 빠져야 함

        List<CategoryRankingSummary> summaries = service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60);

        assertThat(summaries.get(0).topCategories())
                .extracting(TopCategoryItem::categoryName)
                .containsExactly("반도체", "화학", "자동차");
    }

    @Test
    void getTopCategoryRankings_기본_제외_구간은_평균_계산에서_빠진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory root = category(1L, null, "반도체");
        // tier(20L)이 결과에 포함되면 -20%, 빠지면 +10% — 제외가 실제로 적용됐는지 값으로 검증한다.
        stubRankingForTopCategories(
                snapshotTime,
                List.of(root),
                List.of(20L),
                changeRateItem(
                        root.getId(),
                        tier(10L, "대형", 100_000, 10000), // +10%, 포함
                        tier(20L, "소형", -500_000, 10000))); // -50%, 제외 대상

        List<CategoryRankingSummary> summaries = service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60);

        assertThat(summaries.get(0).topCategories().get(0).changeRate()).isEqualByComparingTo(BigDecimal.TEN);
    }

    private void stubRankingForTopCategories(
            LocalDateTime snapshotTime,
            List<MarketMapCategory> categories,
            List<Long> excludedTierIds,
            CategoryChangeRateItem... items) {
        when(marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(List.of(Market.KOSPI), snapshotTime, 60))
                .thenReturn(new SnapshotResponse<>(
                        snapshotTime, List.of(new CategoryChangeRateMarketRanking(Market.KOSPI, List.of(items)))));
        when(marketMapCategoryRepository.findAll()).thenReturn(categories);
        when(marketValueTierThresholdService.getValueTiers())
                .thenReturn(excludedTierIds.stream()
                        .map(id -> new MarketValueTierItem(id, "제외구간", 0L, true))
                        .toList());
        when(marketMapCategoryChangeRateSnapshotService.combine(Mockito.anyList()))
                .thenAnswer(invocation -> combine(invocation.getArgument(0)));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());
    }

    // combine()이 필드를 전혀 참조하지 않는 순수 계산이라, 스텁 대신 같은 식을 여기서 재현해서 쓴다.
    private SnapshotAverages combine(List<CategoryTierBreakdown> breakdowns) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (CategoryTierBreakdown breakdown : breakdowns) {
            weightedSum = weightedSum.add(breakdown.weightedSum());
            totalValue = totalValue.add(breakdown.totalValue());
        }
        BigDecimal weightedAvg =
                totalValue.signum() == 0 ? BigDecimal.ZERO : weightedSum.divide(totalValue, 4, RoundingMode.HALF_UP);
        return new SnapshotAverages(weightedAvg, BigDecimal.ZERO);
    }

    private CategoryChangeRateItem changeRateItem(Long categoryId, CategoryTierBreakdown... breakdowns) {
        return CategoryChangeRateItem.withoutBefore(categoryId, List.of(breakdowns));
    }

    private CategoryTierBreakdown tier(Long tierId, String label, long weightedSum, long totalValue) {
        return new CategoryTierBreakdown(
                tierId,
                label,
                BigDecimal.valueOf(weightedSum),
                BigDecimal.valueOf(totalValue),
                BigDecimal.valueOf(weightedSum),
                1);
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

    private SectorPriceSnapshot priceSnapshot(String stockCode, LocalDateTime snapshotTime, BigDecimal currentPrice) {
        return SectorPriceSnapshot.create(
                Market.KOSPI,
                snapshotTime,
                stockCode,
                ExchangeType.KRX,
                stockCode,
                currentPrice,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
