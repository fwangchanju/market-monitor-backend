package dev.eolmae.marketmonitor.domain.marketmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryTreeNode;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MarketMapCategoryTreeServiceTest {

    private final MarketMapCategoryRepository marketMapCategoryRepository =
            Mockito.mock(MarketMapCategoryRepository.class);
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository =
            Mockito.mock(MarketMapStockCategoryRepository.class);
    private final MarketMapCategoryService marketMapCategoryService = Mockito.mock(MarketMapCategoryService.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final MarketMapCategoryTreeService service = new MarketMapCategoryTreeService(
            marketMapCategoryRepository,
            marketMapStockCategoryRepository,
            marketMapCategoryService,
            stockInfoCacheService,
            new ObjectMapper());

    @Test
    void buildTree_대분류_세부카테고리_직속종목_정확히_조립된다() {
        MarketMapCategory electronics = category(1L, null, "전기/전자");
        MarketMapCategory semiconductor = category(2L, 1L, "반도체");
        MarketMapCategory chemical = category(3L, null, "화학");

        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(electronics, semiconductor, chemical));
        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(
                        MarketMapStockCategory.create("005930", 2L),
                        MarketMapStockCategory.create("000660", 2L),
                        MarketMapStockCategory.create("009150", 1L)));

        List<CategoryTreeNode> tree = service.buildTree();

        assertThat(tree).hasSize(2);
        CategoryTreeNode electronicsNode =
                tree.stream().filter(node -> node.categoryName().equals("전기/전자")).findFirst().orElseThrow();
        assertThat(electronicsNode.stockCodes()).containsExactly("009150");
        assertThat(electronicsNode.children()).hasSize(1);
        assertThat(electronicsNode.children().get(0).categoryName()).isEqualTo("반도체");
        assertThat(electronicsNode.children().get(0).stockCodes()).containsExactly("005930", "000660");

        CategoryTreeNode chemicalNode =
                tree.stream().filter(node -> node.categoryName().equals("화학")).findFirst().orElseThrow();
        assertThat(chemicalNode.stockCodes()).isEmpty();
        assertThat(chemicalNode.children()).isEmpty();
    }

    @Test
    void toJson_parseJson_왕복해도_구조가_동일하다() {
        List<CategoryTreeNode> original = List.of(new CategoryTreeNode(
                "전기/전자",
                List.of(new CategoryTreeNode("반도체", List.of(), List.of("005930", "000660"))),
                List.of("009150")));

        String json = service.toJson(original);
        List<CategoryTreeNode> parsed = service.parseJson(json);

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void restore_라이브_테이블을_스냅샷으로_교체하고_스냅샷에_없는_활성_주권_종목을_채운다() {
        stubCategorySaveWithGeneratedId(100L);
        List<CategoryTreeNode> tree = List.of(new CategoryTreeNode("반도체", List.of(), List.of("005930")));
        // insertNode가 스냅샷대로 복원한 이후 상태를 흉내낸 것: 005930만 배정돼 있고, 000660은 스냅샷 저장 이후 신규 상장된 걸로 가정
        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 100L)));
        StockInfo samsung = stockInfo("005930", "반도체", true, true);
        StockInfo skHynix = stockInfo("000660", "반도체", true, true);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", samsung, "000660", skHynix));

        service.restore(tree, 1L);

        verify(marketMapStockCategoryRepository).deleteAllInBatch();
        verify(marketMapCategoryRepository).deleteAllInBatch();

        ArgumentCaptor<List<StockInfoSyncedEvent.NewStock>> missingCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryService).restoreMissingStockCategories(missingCaptor.capture());
        assertThat(missingCaptor.getValue())
                .extracting(StockInfoSyncedEvent.NewStock::stockCode, StockInfoSyncedEvent.NewStock::categoryName)
                .containsExactly(tuple("000660", "반도체"));
    }

    @Test
    void restore_스냅샷에_없는_활성_주권_종목이_없으면_배정_채우기를_빈_리스트로_호출한다() {
        stubCategorySaveWithGeneratedId(100L);
        List<CategoryTreeNode> tree = List.of(new CategoryTreeNode("반도체", List.of(), List.of("005930")));
        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 100L)));
        StockInfo samsung = stockInfo("005930", "반도체", true, true);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", samsung));

        service.restore(tree, 1L);

        verify(marketMapCategoryService).restoreMissingStockCategories(List.of());
    }

    @Test
    void restore_비활성이거나_비주권인_종목은_배정_채우기_대상에서_제외한다() {
        stubCategorySaveWithGeneratedId(100L);
        List<CategoryTreeNode> tree = List.of(new CategoryTreeNode("반도체", List.of(), List.of("005930")));
        when(marketMapStockCategoryRepository.findAll())
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 100L)));
        StockInfo samsung = stockInfo("005930", "반도체", true, true);
        StockInfo delisted = stockInfo("999999", "반도체", false, true);
        StockInfo etf = stockInfo("888888", "ETF", true, false);
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of("005930", samsung, "999999", delisted, "888888", etf));

        service.restore(tree, 1L);

        verify(marketMapCategoryService).restoreMissingStockCategories(List.of());
    }

    private void stubCategorySaveWithGeneratedId(Long id) {
        // IDENTITY 전략은 insert 시점에 즉시 id가 채워지므로, save가 그 시점을 흉내내도록 stub
        when(marketMapCategoryRepository.save(Mockito.any())).thenAnswer(invocation -> {
            MarketMapCategory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", id);
            return saved;
        });
    }

    private StockInfo stockInfo(String stockCode, String categoryName, boolean active, boolean ordinary) {
        StockInfo stockInfo = StockInfo.create(
                stockCode, stockCode + "-종목", Market.KOSPI, ordinary ? "0" : "8", categoryName, 100L, BigDecimal.TEN);
        if (!active) {
            stockInfo.markInactive();
        }
        return stockInfo;
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
}
