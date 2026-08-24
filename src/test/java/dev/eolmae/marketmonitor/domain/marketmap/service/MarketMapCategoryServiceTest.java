package dev.eolmae.marketmonitor.domain.marketmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent.NewStock;
import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MarketMapCategoryServiceTest {

    private final MarketMapCategoryRepository marketMapCategoryRepository =
            Mockito.mock(MarketMapCategoryRepository.class);
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository =
            Mockito.mock(MarketMapStockCategoryRepository.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final MarketMapCategoryService service = new MarketMapCategoryService(
            marketMapCategoryRepository, marketMapStockCategoryRepository, stockInfoCacheService);

    @Test
    void onStockInfoSynced_없는_카테고리는_생성하고_신규종목을_배정한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        MarketMapCategory electronics = category(2L, null, "전기/전자");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, electronics));
        // IDENTITY 전략은 insert 시점에 즉시 id가 채워지므로, save가 그 시점을 흉내내도록 stub
        when(marketMapCategoryRepository.save(Mockito.any())).thenAnswer(invocation -> {
            MarketMapCategory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        service.onStockInfoSynced(
                new StockInfoSyncedEvent(List.of(new NewStock("005930", "반도체"), new NewStock("051910", "화학"))));

        ArgumentCaptor<MarketMapCategory> categoryCaptor = ArgumentCaptor.forClass(MarketMapCategory.class);
        verify(marketMapCategoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("화학");
        assertThat(categoryCaptor.getValue().getParentId()).isNull();
        Long chemicalId = categoryCaptor.getValue().getId();

        ArgumentCaptor<List<MarketMapStockCategory>> assignmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapStockCategoryRepository).saveAll(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue())
                .extracting(MarketMapStockCategory::getStockCode, MarketMapStockCategory::getCategoryId)
                .containsExactlyInAnyOrder(tuple("005930", 1L), tuple("051910", chemicalId));
    }

    @Test
    void onStockInfoSynced_전부_이미_존재하면_새로_생성하지_않고_배정만_한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));

        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of(new NewStock("005930", "반도체"))));

        verify(marketMapCategoryRepository, never()).save(Mockito.any());

        ArgumentCaptor<List<MarketMapStockCategory>> assignmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapStockCategoryRepository).saveAll(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue())
                .extracting(MarketMapStockCategory::getStockCode, MarketMapStockCategory::getCategoryId)
                .containsExactly(tuple("005930", 1L));
    }

    @Test
    void onStockInfoSynced_신규종목이_없으면_아무것도_하지_않는다() {
        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of()));

        verify(marketMapCategoryRepository, never()).save(Mockito.any());
        verify(marketMapStockCategoryRepository, never()).saveAll(Mockito.anyList());
    }

    @Test
    void getCategories_카테고리_목록을_반환한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        MarketMapCategory chemical = category(2L, null, "화학");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, chemical));

        List<CategoryItem> items = service.getCategories();

        assertThat(items).extracting(CategoryItem::name).containsExactlyInAnyOrder("반도체", "화학");
    }

    @Test
    void deletePreview_배정된_종목이_있으면_종목_목록과_함께_차단된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(1L)))
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 1L)));
        StockInfo samsung = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", samsung));

        CategoryDeletePreview preview = service.deletePreview(1L);

        assertThat(preview.categoryName()).isEqualTo("반도체");
        assertThat(preview.deletable()).isFalse();
        assertThat(preview.blockingStocks())
                .extracting("categoryName", "stockName")
                .containsExactly(tuple("반도체", "삼성전자"));
    }

    @Test
    void deletePreview_배정된_종목이_없으면_삭제_가능하고_하위카테고리_목록을_반환한다() {
        MarketMapCategory electronics = category(1L, null, "전기/전자");
        MarketMapCategory semiconductor = category(2L, 1L, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(electronics, semiconductor));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(1L, 2L)))
                .thenReturn(List.of());

        CategoryDeletePreview preview = service.deletePreview(1L);

        assertThat(preview.categoryName()).isEqualTo("전기/전자");
        assertThat(preview.deletable()).isTrue();
        assertThat(preview.deletableCategories()).containsExactly("반도체");
    }

    @Test
    void deletePreview_존재하지_않는_카테고리는_404를_반환한다() {
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.deletePreview(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_배정된_종목이_있으면_409로_차단된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(1L)))
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 1L)));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_성공하면_대상과_하위카테고리가_삭제된다() {
        MarketMapCategory parent = category(1L, null, "부모");
        MarketMapCategory a = category(2L, 1L, "A");
        MarketMapCategory target = category(3L, 1L, "삭제대상");
        MarketMapCategory b = category(4L, 1L, "B");
        MarketMapCategory child = category(5L, 3L, "자식");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parent, a, target, b, child));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(3L, 5L)))
                .thenReturn(List.of());

        service.delete(3L);

        ArgumentCaptor<List<MarketMapCategory>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(target, child);
    }

    @Test
    void reparent_부모와_depth가_바뀐다() {
        MarketMapCategory parentA = category(1L, null, "A");
        MarketMapCategory parentB = category(2L, null, "B");
        MarketMapCategory x = category(3L, 1L, "X");
        MarketMapCategory y = category(4L, 1L, "Y");
        MarketMapCategory z = category(5L, 2L, "Z");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parentA, parentB, x, y, z));

        service.reparent(4L, 2L);

        assertThat(y.getParentId()).isEqualTo(2L);
        assertThat(y.getDepth()).isEqualTo(1);
    }

    @Test
    void reparent_새_부모가_null이면_최상위로_이동한다() {
        MarketMapCategory parent = category(1L, null, "부모");
        MarketMapCategory child = category(2L, 1L, "자식");
        MarketMapCategory grandchild = category(3L, 2L, "손자");
        ReflectionTestUtils.setField(grandchild, "depth", 2); // 실제 부모(자식)는 depth 1인데, 헬퍼는 placeholder를 항상 depth 0으로 가정하므로 보정
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parent, child, grandchild));

        service.reparent(2L, null);

        assertThat(child.getParentId()).isNull();
        assertThat(child.getDepth()).isEqualTo(0);
        assertThat(grandchild.getDepth()).isEqualTo(1);
    }

    @Test
    void reparent_이동한_카테고리의_하위_카테고리도_depth가_함께_바뀐다() {
        MarketMapCategory e = category(1L, null, "E");
        MarketMapCategory d = category(2L, 1L, "D");
        MarketMapCategory a = category(3L, null, "A");
        MarketMapCategory b = category(4L, 3L, "B");
        MarketMapCategory c = category(5L, 4L, "C");
        ReflectionTestUtils.setField(c, "depth", 2); // 실제 부모(B)는 depth 1인데, 헬퍼는 placeholder를 항상 depth 0으로 가정하므로 보정
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(e, d, a, b, c));

        service.reparent(4L, 2L);

        assertThat(b.getParentId()).isEqualTo(2L);
        assertThat(b.getDepth()).isEqualTo(2);
        assertThat(c.getDepth()).isEqualTo(3);
    }

    @Test
    void reparent_새_부모가_자기_자신이면_409를_던진다() {
        MarketMapCategory a = category(1L, null, "A");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reparent(1L, 1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void reparent_새_부모가_자신의_하위_카테고리면_409를_던진다() {
        MarketMapCategory a = category(1L, null, "A");
        MarketMapCategory b = category(2L, 1L, "B");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reparent(1L, 2L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void exclude_카테고리를_제외_상태로_바꾼다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findById(1L)).thenReturn(Optional.of(semiconductor));

        service.exclude(1L);

        assertThat(semiconductor.isExcluded()).isTrue();
    }

    @Test
    void include_카테고리를_제외_해제한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        semiconductor.exclude();
        when(marketMapCategoryRepository.findById(1L)).thenReturn(Optional.of(semiconductor));

        service.include(1L);

        assertThat(semiconductor.isExcluded()).isFalse();
    }

    @Test
    void exclude_존재하지_않는_카테고리면_404를_던진다() {
        when(marketMapCategoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exclude(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void resetExcludes_전체_카테고리의_제외_상태를_해제한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체");
        MarketMapCategory chemical = category(2L, null, "화학");
        semiconductor.exclude();
        chemical.exclude();
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, chemical));

        service.resetExcludes();

        assertThat(semiconductor.isExcluded()).isFalse();
        assertThat(chemical.isExcluded()).isFalse();
    }

    @Test
    void reparent_존재하지_않는_카테고리면_404를_던진다() {
        MarketMapCategory a = category(1L, null, "A");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reparent(99L, 1L)).isInstanceOf(NotFoundException.class);
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
