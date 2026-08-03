package dev.eolmae.marketmonitor.domain.marketmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryDeletePreview;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class MarketMapCategoryServiceTest {

    private final MarketMapCategoryRepository marketMapCategoryRepository = Mockito.mock(MarketMapCategoryRepository.class);
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository =
            Mockito.mock(MarketMapStockCategoryRepository.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final MarketMapCategoryService service =
            new MarketMapCategoryService(marketMapCategoryRepository, marketMapStockCategoryRepository, stockInfoCacheService);

    @Test
    void onStockInfoSynced_없는_카테고리만_최상위로_생성하고_기존은_건너뛴다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1);
        MarketMapCategory electronics = category(2L, null, "전기/전자", 2);
        MarketMapCategory memory = category(3L, 2L, "메모리", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, electronics, memory));

        service.onStockInfoSynced(new StockInfoSyncedEvent(Set.of("반도체", "화학", "미분류")));

        ArgumentCaptor<List<MarketMapCategory>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryRepository).saveAll(captor.capture());
        List<MarketMapCategory> saved = captor.getValue();

        assertThat(saved).extracting(MarketMapCategory::getName).containsExactlyInAnyOrder("화학", "미분류");
        assertThat(saved).allMatch(category -> category.getParentId() == null);
        // 자식(메모리)의 displayOrder는 최상위 maxOrder 계산에서 제외되고, 기존 최상위 최댓값(2) 다음부터 이어진다.
        assertThat(saved).extracting(MarketMapCategory::getDisplayOrder).containsExactlyInAnyOrder(3, 4);
        // 이벤트에 포함된 기존 카테고리(반도체)는 isSynced로 갱신되고, 포함 안 된 건 그대로다.
        assertThat(semiconductor.isSynced()).isTrue();
        assertThat(electronics.isSynced()).isFalse();
        assertThat(memory.isSynced()).isFalse();
    }

    @Test
    void onStockInfoSynced_전부_이미_존재하면_새로_생성하지_않고_isSynced만_갱신한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));

        service.onStockInfoSynced(new StockInfoSyncedEvent(Set.of("반도체")));

        ArgumentCaptor<List<MarketMapCategory>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
        assertThat(semiconductor.isSynced()).isTrue();
    }

    @Test
    void getCategories_isSynced_필드가_그대로_노출된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1, true);
        MarketMapCategory chemical = category(2L, null, "화학", 2, false);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, chemical));

        List<CategoryItem> items = service.getCategories();

        assertThat(items)
                .extracting(CategoryItem::name, CategoryItem::isSynced)
                .containsExactlyInAnyOrder(tuple("반도체", true), tuple("화학", false));
    }

    @Test
    void deletePreview_isSynced인_카테고리는_이유_없이_차단된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1, true);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));

        CategoryDeletePreview preview = service.deletePreview(1L);

        assertThat(preview.categoryName()).isEqualTo("반도체");
        assertThat(preview.deletable()).isFalse();
        assertThat(preview.blockingStocks()).isEmpty();
    }

    @Test
    void deletePreview_배정된_종목이_있으면_종목_목록과_함께_차단된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1, false);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(1L)))
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 1L)));
        StockInfo samsung = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", samsung));

        CategoryDeletePreview preview = service.deletePreview(1L);

        assertThat(preview.categoryName()).isEqualTo("반도체");
        assertThat(preview.deletable()).isFalse();
        assertThat(preview.blockingStocks()).extracting("categoryName", "stockName").containsExactly(tuple("반도체", "삼성전자"));
    }

    @Test
    void deletePreview_배정된_종목이_없으면_삭제_가능하고_하위카테고리_목록을_반환한다() {
        MarketMapCategory electronics = category(1L, null, "전기/전자", 1, false);
        MarketMapCategory semiconductor = category(2L, 1L, "반도체", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(electronics, semiconductor));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(1L, 2L))).thenReturn(List.of());

        CategoryDeletePreview preview = service.deletePreview(1L);

        assertThat(preview.categoryName()).isEqualTo("전기/전자");
        assertThat(preview.deletable()).isTrue();
        assertThat(preview.deletableCategories()).containsExactly("반도체");
    }

    @Test
    void deletePreview_존재하지_않는_카테고리는_404를_반환한다() {
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.deletePreview(1L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_isSynced인_카테고리는_409로_차단된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1, true);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_배정된_종목이_있으면_409로_차단된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1, false);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(1L)))
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 1L)));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResponseStatusException.class);
    }

    private MarketMapCategory category(Long id, Long parentId, String name, int displayOrder) {
        return category(id, parentId, name, displayOrder, false);
    }

    private MarketMapCategory category(Long id, Long parentId, String name, int displayOrder, boolean isSynced) {
        MarketMapCategory category = parentId == null
                ? MarketMapCategory.createParent(name, displayOrder, isSynced)
                : categoryWithParent(parentId, name, displayOrder);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private MarketMapCategory categoryWithParent(Long parentId, String name, int displayOrder) {
        MarketMapCategory parent = MarketMapCategory.createParent("parent-placeholder", 0, false);
        ReflectionTestUtils.setField(parent, "id", parentId);
        return MarketMapCategory.createChild(name, parent, displayOrder);
    }
}
