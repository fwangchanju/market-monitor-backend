package dev.eolmae.marketmonitor.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketMapExcludedStockRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.StockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapCategoryNode;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import java.math.BigDecimal;
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
    private final StockCategoryRepository stockCategoryRepository = Mockito.mock(StockCategoryRepository.class);
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository = Mockito.mock(SectorPriceSnapshotRepository.class);
    private final MarketMapExcludedStockRepository marketMapExcludedStockRepository =
            Mockito.mock(MarketMapExcludedStockRepository.class);
    private final MarketMapCategoryRepository marketMapCategoryRepository = Mockito.mock(MarketMapCategoryRepository.class);
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository =
            Mockito.mock(MarketMapStockCategoryRepository.class);
    private final MarketMapQueryService service = new MarketMapQueryService(
            stockInfoCacheService,
            stockCategoryRepository,
            sectorPriceSnapshotRepository,
            marketMapExcludedStockRepository,
            marketMapCategoryRepository,
            marketMapStockCategoryRepository);

    @Test
    void getCustomMarketMap_트리집계와_카테고리명_매칭이_정확히_반영된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        MarketMapCategory electronics = category(1L, null, "전기/전자", 1);
        MarketMapCategory semiconductor = category(2L, 1L, "반도체", 1);
        MarketMapCategory chemical = category(3L, null, "화학", 2);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(electronics, semiconductor, chemical));

        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(
                        MarketMapStockCategory.create("005930", 2L),
                        MarketMapStockCategory.create("000660", 2L),
                        MarketMapStockCategory.create("009150", 1L)));

        StockInfo samsung = stockInfo("005930", "삼성전자", null, 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", null, 50L, BigDecimal.valueOf(20));
        StockInfo lgElectronics = stockInfo("009150", "삼성전기", null, 200L, BigDecimal.valueOf(5));
        // market_map_stock_category에 배정 없음 -> stock_info 카테고리명("화학")으로 매치
        StockInfo lgChem = stockInfo("051910", "LG화학", "화학", 500L, BigDecimal.ONE);

        Map<String, StockInfo> stockInfoCache = List.of(samsung, skHynix, lgElectronics, lgChem).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findFirstByMarketTypeOrderBySnapshotTimeDesc(Market.KOSPI))
                .thenReturn(Optional.of(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(sectorPriceSnapshotRepository.findByMarketTypeAndSnapshotTime(Market.KOSPI, snapshotTime))
                .thenReturn(List.of(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20)),
                        priceSnapshot("009150", snapshotTime, BigDecimal.valueOf(5)),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));

        SnapshotResponse<MarketMapCategoryNode> response = service.getCustomMarketMap(Market.KOSPI, false);

        assertThat(response.snapshotTime()).isEqualTo(snapshotTime);
        List<MarketMapCategoryNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapCategoryNode electronicsNode =
                nodes.stream().filter(node -> node.categoryName().equals("전기/전자")).findFirst().orElseThrow();
        assertThat(electronicsNode.items()).extracting("stockCode").containsExactly("009150");
        assertThat(electronicsNode.children()).hasSize(1);

        MarketMapCategoryNode semiconductorNode = electronicsNode.children().get(0);
        assertThat(semiconductorNode.categoryName()).isEqualTo("반도체");
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactlyInAnyOrder("005930", "000660");
        assertThat(semiconductorNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        // 전기/전자 총액 = 직속(009150: 5*200=1000) + 자식(반도체: 2000) = 3000
        assertThat(electronicsNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(3000));

        MarketMapCategoryNode chemicalNode =
                nodes.stream().filter(node -> node.categoryName().equals("화학")).findFirst().orElseThrow();
        assertThat(chemicalNode.children()).isEmpty();
        assertThat(chemicalNode.items()).extracting("stockCode").containsExactly("051910");
        assertThat(chemicalNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void getCustomMarketMap_카테고리가_전부_평평하게_자동생성된_상태에서도_이름매칭으로_그룹핑된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        // 어드민이 계층을 구성하기 전, 수집기가 stock_info 카테고리명으로 자동 생성한 직후의 상태(전부 root)
        MarketMapCategory chemical = category(1L, null, "화학", 1);
        MarketMapCategory semiconductor = category(2L, null, "반도체", 2);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(chemical, semiconductor));
        when(marketMapStockCategoryRepository.findAll()).thenReturn(List.of());

        StockInfo lgChem = stockInfo("051910", "LG화학", "화학", 500L, BigDecimal.ONE);
        StockInfo samsung = stockInfo("005930", "삼성전자", "반도체", 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", "반도체", 50L, BigDecimal.valueOf(20));

        Map<String, StockInfo> stockInfoCache = List.of(lgChem, samsung, skHynix).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findFirstByMarketTypeOrderBySnapshotTimeDesc(Market.KOSPI))
                .thenReturn(Optional.of(priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));
        when(sectorPriceSnapshotRepository.findByMarketTypeAndSnapshotTime(Market.KOSPI, snapshotTime))
                .thenReturn(List.of(
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE),
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20))));

        SnapshotResponse<MarketMapCategoryNode> response = service.getCustomMarketMap(Market.KOSPI, false);

        assertThat(response.snapshotTime()).isEqualTo(snapshotTime);
        List<MarketMapCategoryNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapCategoryNode chemicalNode =
                nodes.stream().filter(node -> node.categoryName().equals("화학")).findFirst().orElseThrow();
        assertThat(chemicalNode.children()).isEmpty();
        assertThat(chemicalNode.items()).extracting("stockCode").containsExactly("051910");
        assertThat(chemicalNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(500));

        MarketMapCategoryNode semiconductorNode =
                nodes.stream().filter(node -> node.categoryName().equals("반도체")).findFirst().orElseThrow();
        assertThat(semiconductorNode.children()).isEmpty();
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactlyInAnyOrder("005930", "000660");
        assertThat(semiconductorNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));
    }

    @Test
    void getDefaultMarketMap_stock_info_카테고리_그대로_1뎁스_노드로_묶인다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);

        StockInfo samsung = stockInfo("005930", "삼성전자", "반도체", 100L, BigDecimal.TEN);
        StockInfo skHynix = stockInfo("000660", "SK하이닉스", "반도체", 50L, BigDecimal.valueOf(20));
        StockInfo lgChem = stockInfo("051910", "LG화학", "", 500L, BigDecimal.ONE);

        Map<String, StockInfo> stockInfoCache = List.of(samsung, skHynix, lgChem).stream()
                .collect(Collectors.toMap(StockInfo::getStockCode, Function.identity()));
        when(stockInfoCacheService.getCache()).thenReturn(stockInfoCache);

        when(sectorPriceSnapshotRepository.findFirstByMarketTypeOrderBySnapshotTimeDesc(Market.KOSPI))
                .thenReturn(Optional.of(priceSnapshot("005930", snapshotTime, BigDecimal.TEN)));
        when(sectorPriceSnapshotRepository.findByMarketTypeAndSnapshotTime(Market.KOSPI, snapshotTime))
                .thenReturn(List.of(
                        priceSnapshot("005930", snapshotTime, BigDecimal.TEN),
                        priceSnapshot("000660", snapshotTime, BigDecimal.valueOf(20)),
                        priceSnapshot("051910", snapshotTime, BigDecimal.ONE)));

        SnapshotResponse<MarketMapCategoryNode> response = service.getDefaultMarketMap(Market.KOSPI, false);

        assertThat(response.snapshotTime()).isEqualTo(snapshotTime);
        List<MarketMapCategoryNode> nodes = response.items();
        assertThat(nodes).hasSize(2);

        MarketMapCategoryNode semiconductorNode =
                nodes.stream().filter(node -> node.categoryName().equals("반도체")).findFirst().orElseThrow();
        assertThat(semiconductorNode.children()).isEmpty();
        assertThat(semiconductorNode.items()).extracting("stockCode").containsExactlyInAnyOrder("005930", "000660");
        assertThat(semiconductorNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        MarketMapCategoryNode uncategorizedNode =
                nodes.stream().filter(node -> node.categoryName().equals("미분류")).findFirst().orElseThrow();
        assertThat(uncategorizedNode.children()).isEmpty();
        assertThat(uncategorizedNode.items()).extracting("stockCode").containsExactly("051910");
        assertThat(uncategorizedNode.totalMarketValue()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    private MarketMapCategory category(Long id, Long parentId, String name, int displayOrder) {
        MarketMapCategory category = parentId == null
                ? MarketMapCategory.createParent(name, displayOrder, false)
                : categoryWithParent(parentId, name, displayOrder);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private MarketMapCategory categoryWithParent(Long parentId, String name, int displayOrder) {
        MarketMapCategory parent = MarketMapCategory.createParent("parent-placeholder", 0, false);
        ReflectionTestUtils.setField(parent, "id", parentId);
        return MarketMapCategory.createChild(name, parent, displayOrder);
    }

    private StockInfo stockInfo(String stockCode, String stockName, String categoryName, Long listCount, BigDecimal lastPrice) {
        return StockInfo.create(stockCode, stockName, Market.KOSPI, "0", categoryName, listCount, lastPrice);
    }

    private SectorPriceSnapshot priceSnapshot(String stockCode, LocalDateTime snapshotTime, BigDecimal currentPrice) {
        return SectorPriceSnapshot.create(
                Market.KOSPI, snapshotTime, stockCode, stockCode, currentPrice, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
