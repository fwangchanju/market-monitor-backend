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
import dev.eolmae.marketmonitor.domain.view.enums.AverageMode;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
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
        // beforeMinutes=60이므로 09:00 시각도 별도로 조회한다 — now/before 둘 다 값이 있는 일반적인 경우.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime.minusMinutes(60)))
                .thenReturn(List.of(
                        marketOverviewSnapshot(Market.KOSPI, snapshotTime.minusMinutes(60), BigDecimal.valueOf(0.98)),
                        marketOverviewSnapshot(
                                Market.KOSDAQ, snapshotTime.minusMinutes(60), BigDecimal.valueOf(-0.20))));

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.ALL_STOCK, 60);

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
        CategoryChangeRateMarketRanking kospiRanking = new CategoryChangeRateMarketRanking(
                Market.KOSPI, List.of(CategoryChangeRateItem.withoutBefore(1L, List.of())));
        when(marketMapCategoryChangeRateSnapshotService.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(List.of(Market.KOSPI), snapshotTime, 60))
                .thenReturn(new SnapshotResponse<>(snapshotTime, List.of(kospiRanking)));
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(category(1L, null, "반도체")));
        // 이번 수집 주기에 지수기여도랭킹 수집만 실패해서, 카테고리 랭킹은 있는데 지수 스냅샷은 그 시각에
        // 없는 경우 — 다른 시각 값으로 조용히 대체하지 않고 index 전체가 null로 내려간다.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime)).thenReturn(List.of());

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.KOSPI, 60);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).index()).isNull();
    }

    @Test
    void getCategoryChangeRates_before_시각에_지수_스냅샷이_없으면_index_before가_null이다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        CategoryChangeRateMarketRanking kospiRanking = new CategoryChangeRateMarketRanking(
                Market.KOSPI, List.of(CategoryChangeRateItem.withoutBefore(1L, List.of())));
        when(marketMapCategoryChangeRateSnapshotService.findLatestCommonSnapshotTime(List.of(Market.KOSPI)))
                .thenReturn(Optional.of(snapshotTime));
        when(marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(List.of(Market.KOSPI), snapshotTime, 60))
                .thenReturn(new SnapshotResponse<>(snapshotTime, List.of(kospiRanking)));
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(category(1L, null, "반도체")));
        // now 시각(10:00)에는 지수 스냅샷이 있지만, before 시각(09:00, beforeMinutes=60)에는 없는
        // 경우 — 장 시작 직후나 수집 gap. 가까운 다른 시점 값으로 대체하지 않고 before만 null이다.
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime))
                .thenReturn(List.of(marketOverviewSnapshot(Market.KOSPI, snapshotTime, BigDecimal.valueOf(1.23))));
        when(marketOverviewSnapshotRepository.findBySnapshotTime(snapshotTime.minusMinutes(60)))
                .thenReturn(List.of());

        SnapshotResponse<CategoryChangeRateMarketRanking> response =
                service.getCategoryChangeRates(MarketQuery.KOSPI, 60);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).index().now()).isEqualByComparingTo(BigDecimal.valueOf(1.23));
        assertThat(response.items().get(0).index().before()).isNull();
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
                changeRateItemWithFlatBefore(root.getId(), tier(10L, "대형", 50_000, 10000)), // +5%p
                changeRateItemWithFlatBefore(child.getId(), tier(10L, "대형", 900_000, 10000))); // +90%p

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
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        MarketMapCategory c = category(3L, null, "자동차");
        stubRankingForTopCategories(
                snapshotTime,
                List.of(a, b, c),
                List.of(),
                changeRateItemWithFlatBefore(a.getId(), tier(10L, "대형", 100_000, 10000)), // +10%p
                changeRateItemWithFlatBefore(b.getId(), tier(10L, "대형", 50_000, 10000)), // +5%p
                changeRateItemWithFlatBefore(c.getId(), tier(10L, "대형", 20_000, 10000))); // +2%p, 3위라 빠져야 함

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
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        stubRankingForTopCategories(
                snapshotTime,
                List.of(a, b),
                List.of(),
                changeRateItem(a.getId(), tier(10L, "대형", 900_000, 10000)), // +90%, before 없음
                changeRateItemWithFlatBefore(b.getId(), tier(10L, "대형", 50_000, 10000))); // +5%p

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
        stubRankingForTopCategories(
                snapshotTime, List.of(a), List.of(), changeRateItem(a.getId(), tier(10L, "대형", 100_000, 10000)));

        List<CategoryRankingSummary> summaries =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topCategories()).isEmpty();
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
                changeRateItemWithFlatBefore(
                        root.getId(),
                        tier(10L, "대형", 100_000, 10000), // +10%p, 포함
                        tier(20L, "소형", -500_000, 10000))); // -50%p, 제외 대상

        List<CategoryRankingSummary> summaries =
                service.getTopCategoryRankings(MarketQuery.KOSPI, snapshotTime, 60, AverageMode.WEIGHTED, false);

        assertThat(summaries.get(0).topCategories().get(0).changeRate()).isEqualByComparingTo(BigDecimal.TEN);
    }

    // 델타(now-before) 기준과 등락률(now) 기준이 서로 다른 카테고리를 뽑을 수 있음을 보인다 — 08:10
    // 폴백이 델타 대신 이 랭킹을 쓰는 이유다.
    @Test
    void getTopCategoryRankingsByChangeRate_등락률_기준으로_델타_기준과_다른_카테고리를_뽑는다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        MarketMapCategory b = category(2L, null, "화학");
        MarketMapCategory c = category(3L, null, "자동차");
        // a: now +20%, before +19% → 델타 +1%p. b: now +5%, before -10% → 델타 +15%p.
        // c: now +10%, before +8% → 델타 +2%p.
        CategoryChangeRateItem itemA = CategoryChangeRateItem.withBefore(
                a.getId(), List.of(tier(10L, "대형", 200_000, 10_000)), List.of(tier(10L, "대형", 190_000, 10_000)));
        CategoryChangeRateItem itemB = CategoryChangeRateItem.withBefore(
                b.getId(), List.of(tier(10L, "대형", 50_000, 10_000)), List.of(tier(10L, "대형", -100_000, 10_000)));
        CategoryChangeRateItem itemC = CategoryChangeRateItem.withBefore(
                c.getId(), List.of(tier(10L, "대형", 100_000, 10_000)), List.of(tier(10L, "대형", 80_000, 10_000)));
        stubRankingForTopCategories(snapshotTime, List.of(a, b, c), List.of(), itemA, itemB, itemC);

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
        stubMergedRanking(List.of(a, b, c));

        // a는 KOSPI 단독으로 보면 압도적 1위(+100%)지만 KOSDAQ에서 -90%라 합치면 +5%로 밀린다.
        Map<Long, List<CategoryTierBreakdown>> kospiBreakdowns = Map.of(
                a.getId(), List.of(tier(10L, "대형", 1_000_000, 10_000)),
                b.getId(), List.of(tier(10L, "대형", 80_000, 10_000)),
                c.getId(), List.of(tier(10L, "대형", 60_000, 10_000)));
        Map<Long, List<CategoryTierBreakdown>> kosdaqBreakdowns = Map.of(
                a.getId(), List.of(tier(10L, "대형", -900_000, 10_000)),
                b.getId(), List.of(tier(10L, "대형", 80_000, 10_000)));
        when(marketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId(
                        List.of(Market.KOSPI, Market.KOSDAQ), snapshotTime))
                .thenReturn(Map.of(Market.KOSPI, kospiBreakdowns, Market.KOSDAQ, kosdaqBreakdowns));

        List<TopCategoryItem> merged =
                service.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false);

        // 병합 평균: a=(1,000,000-900,000)/20,000=+5%, b=(80,000+80,000)/20,000=+8%, c=60,000/10,000=+6%
        assertThat(merged).extracting(TopCategoryItem::categoryName).containsExactly("화학", "자동차");
    }

    @Test
    void getMergedTopCategoryRanking_원시값을_합산한_뒤_한_번만_나눈다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        stubMergedRanking(List.of(a));

        // KOSPI 시총 10,000에 +10%p, KOSDAQ 시총 40,000에 -2%p — 단순 평균이면 (10-2)/2=+4가 되지만,
        // KOSDAQ 쪽 시총 비중이 훨씬 커서 원시값을 합산한 뒤 나누면 +0.4가 맞다.
        Map<Long, List<CategoryTierBreakdown>> kospiBreakdowns =
                Map.of(a.getId(), List.of(tier(10L, "대형", 100_000, 10_000)));
        Map<Long, List<CategoryTierBreakdown>> kosdaqBreakdowns =
                Map.of(a.getId(), List.of(tier(10L, "대형", -80_000, 40_000)));
        when(marketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId(
                        List.of(Market.KOSPI, Market.KOSDAQ), snapshotTime))
                .thenReturn(Map.of(Market.KOSPI, kospiBreakdowns, Market.KOSDAQ, kosdaqBreakdowns));

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
        stubMergedRanking(List.of(category(1L, null, "반도체")));
        when(marketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId(
                        List.of(Market.KOSPI, Market.KOSDAQ), snapshotTime))
                .thenReturn(Map.of());

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
        stubMergedRanking(List.of(excluded, included));

        Map<Long, List<CategoryTierBreakdown>> kospiBreakdowns = Map.of(
                excluded.getId(), List.of(tier(10L, "대형", 900_000, 10_000)), // +90%, 제외 대상이면 빠져야 함
                included.getId(), List.of(tier(10L, "대형", 50_000, 10_000))); // +5%
        when(marketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId(
                        List.of(Market.KOSPI, Market.KOSDAQ), snapshotTime))
                .thenReturn(Map.of(Market.KOSPI, kospiBreakdowns));

        List<TopCategoryItem> filtered =
                service.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, true);
        List<TopCategoryItem> unfiltered =
                service.getMergedTopCategoryRanking(MarketQuery.ALL_STOCK, snapshotTime, AverageMode.WEIGHTED, false);

        assertThat(filtered).extracting(TopCategoryItem::categoryName).containsExactly("화학");
        assertThat(unfiltered).extracting(TopCategoryItem::categoryName).containsExactlyInAnyOrder("반도체", "화학");
    }

    // 가중평균과 산술평균이 실제로 다른 값이 나오는 것을 보여준다 — 4-arg tier()만 쓰는 기존 픽스처는
    // itemCount가 항상 1이라 두 평균이 우연히 같아서 이 분기를 검증하지 못한다.
    @Test
    void getTopCategoryRankingsByChangeRate_평균_방식에_따라_결과가_달라진다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        MarketMapCategory a = category(1L, null, "반도체");
        // 시총 90,000짜리 종목 +30%, 시총 10,000짜리 종목 +10% — 가중평균은 시총이 큰 쪽에 끌려 +28%,
        // 산술평균은 종목당 등락률을 그대로 평균내 +20%.
        stubRankingForTopCategories(
                snapshotTime,
                List.of(a),
                List.of(),
                changeRateItem(a.getId(), tier(10L, "대형", 2_800_000, 100_000, 40, 2)));

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
        MarketMapCategory a = category(1L, null, "반도체");
        // now:    시총 90,000 +30% / 10,000 +10%  → 가중 +28%, 산술 +20%
        // before: 시총 80,000 +10% / 20,000   0%  → 가중  +8%, 산술  +5%
        // → 가중 델타는 +20%p, 산술 델타는 +15%p로 서로 다르다.
        CategoryChangeRateItem item = CategoryChangeRateItem.withBefore(
                a.getId(),
                List.of(tier(10L, "대형", 2_800_000, 100_000, 40, 2)),
                List.of(tier(10L, "대형", 800_000, 100_000, 10, 2)));
        stubRankingForTopCategories(snapshotTime, List.of(a), List.of(), item);

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
        stubRankingForTopCategories(
                snapshotTime,
                List.of(excluded, included),
                List.of(),
                changeRateItem(excluded.getId(), tier(10L, "대형", 900_000, 10000)), // +90%, 제외 대상이면 빠져야 함
                changeRateItem(included.getId(), tier(10L, "대형", 50_000, 10000))); // +5%

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

    private void stubMergedRanking(List<MarketMapCategory> categories) {
        when(marketMapCategoryRepository.findAll()).thenReturn(categories);
        when(marketValueTierThresholdService.getValueTiers()).thenReturn(List.of());
        when(marketMapCategoryChangeRateSnapshotService.combine(Mockito.anyList()))
                .thenAnswer(invocation -> combine(invocation.getArgument(0)));
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
        BigDecimal simpleSum = BigDecimal.ZERO;
        int itemCount = 0;
        for (CategoryTierBreakdown breakdown : breakdowns) {
            weightedSum = weightedSum.add(breakdown.weightedSum());
            totalValue = totalValue.add(breakdown.totalValue());
            simpleSum = simpleSum.add(breakdown.simpleSum());
            itemCount += breakdown.itemCount();
        }
        BigDecimal weightedAvg =
                totalValue.signum() == 0 ? BigDecimal.ZERO : weightedSum.divide(totalValue, 4, RoundingMode.HALF_UP);
        BigDecimal simpleAvg = itemCount == 0
                ? BigDecimal.ZERO
                : simpleSum.divide(BigDecimal.valueOf(itemCount), 4, RoundingMode.HALF_UP);
        return new SnapshotAverages(weightedAvg, simpleAvg);
    }

    private CategoryChangeRateItem changeRateItem(Long categoryId, CategoryTierBreakdown... breakdowns) {
        return CategoryChangeRateItem.withoutBefore(categoryId, List.of(breakdowns));
    }

    /** 랭킹 값은 now - before이므로, before를 0%로 두면 델타가 곧 now가 되어 기대값을 읽기 쉽다.
     * 같은 tierId를 써야 기본 제외 구간 필터가 now/before 양쪽에 똑같이 걸린다. */
    private CategoryChangeRateItem changeRateItemWithFlatBefore(
            Long categoryId, CategoryTierBreakdown... nowBreakdowns) {
        List<CategoryTierBreakdown> before = Arrays.stream(nowBreakdowns)
                .map(b -> tier(b.tierId(), b.tierLabel(), 0L, b.totalValue().longValue()))
                .toList();
        return CategoryChangeRateItem.withBefore(categoryId, List.of(nowBreakdowns), before);
    }

    /** 가중평균만 검증하는 기존 픽스처용 — itemCount 1에 simpleSum을 종목당 등락률(weightedSum/totalValue,
     * Σ가 아니라 1건짜리 평균)로 채워서 산술평균이 가중평균과 실제로 같아지게 한다(6-arg tier()의 특수
     * 케이스일 뿐, 산술평균 자체를 검증하는 데는 쓰지 않는다). */
    private CategoryTierBreakdown tier(Long tierId, String label, long weightedSum, long totalValue) {
        return tier(tierId, label, weightedSum, totalValue, weightedSum / totalValue, 1);
    }

    /** 가중평균과 산술평균이 실제로 달라지는 픽스처용 — simpleSum·itemCount를 따로 받는다. */
    private CategoryTierBreakdown tier(
            Long tierId, String label, long weightedSum, long totalValue, long simpleSum, int itemCount) {
        return new CategoryTierBreakdown(
                tierId,
                label,
                BigDecimal.valueOf(weightedSum),
                BigDecimal.valueOf(totalValue),
                BigDecimal.valueOf(simpleSum),
                itemCount);
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
