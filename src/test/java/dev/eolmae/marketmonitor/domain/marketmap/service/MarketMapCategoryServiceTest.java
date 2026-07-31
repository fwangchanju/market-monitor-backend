package dev.eolmae.marketmonitor.domain.marketmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

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
    }

    @Test
    void onStockInfoSynced_전부_이미_존재하면_아무것도_생성하지_않는다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));

        service.onStockInfoSynced(new StockInfoSyncedEvent(Set.of("반도체")));

        ArgumentCaptor<List<MarketMapCategory>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    private MarketMapCategory category(Long id, Long parentId, String name, int displayOrder) {
        MarketMapCategory category = parentId == null
                ? MarketMapCategory.createParent(name, displayOrder)
                : categoryWithParent(parentId, name, displayOrder);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private MarketMapCategory categoryWithParent(Long parentId, String name, int displayOrder) {
        MarketMapCategory parent = MarketMapCategory.createParent("parent-placeholder", 0);
        ReflectionTestUtils.setField(parent, "id", parentId);
        return MarketMapCategory.createChild(name, parent, displayOrder);
    }
}
