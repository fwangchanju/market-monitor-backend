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
    void onStockInfoSynced_없는_카테고리는_생성하고_신규종목을_배정한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1);
        MarketMapCategory electronics = category(2L, null, "전기/전자", 2);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, electronics));
        // IDENTITY 전략은 insert 시점에 즉시 id가 채워지므로, saveAll이 그 시점을 흉내내도록 stub
        when(marketMapCategoryRepository.saveAll(Mockito.anyList())).thenAnswer(invocation -> {
            List<MarketMapCategory> saved = invocation.getArgument(0);
            long nextId = 100L;
            for (MarketMapCategory category : saved) {
                ReflectionTestUtils.setField(category, "id", nextId++);
            }
            return saved;
        });

        service.onStockInfoSynced(new StockInfoSyncedEvent(
                List.of(new NewStock("005930", "반도체"), new NewStock("051910", "화학"))));

        ArgumentCaptor<List<MarketMapCategory>> categoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryRepository).saveAll(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue()).extracting(MarketMapCategory::getName).containsExactly("화학");
        assertThat(categoryCaptor.getValue()).allMatch(category -> category.getParentId() == null);
        Long chemicalId = categoryCaptor.getValue().get(0).getId();

        ArgumentCaptor<List<MarketMapStockCategory>> assignmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapStockCategoryRepository).saveAll(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue())
                .extracting(MarketMapStockCategory::getStockCode, MarketMapStockCategory::getCategoryId)
                .containsExactlyInAnyOrder(tuple("005930", 1L), tuple("051910", chemicalId));
    }

    @Test
    void onStockInfoSynced_전부_이미_존재하면_새로_생성하지_않고_배정만_한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));

        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of(new NewStock("005930", "반도체"))));

        ArgumentCaptor<List<MarketMapCategory>> categoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryRepository).saveAll(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue()).isEmpty();

        ArgumentCaptor<List<MarketMapStockCategory>> assignmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapStockCategoryRepository).saveAll(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue())
                .extracting(MarketMapStockCategory::getStockCode, MarketMapStockCategory::getCategoryId)
                .containsExactly(tuple("005930", 1L));
    }

    @Test
    void onStockInfoSynced_신규종목이_없으면_아무것도_하지_않는다() {
        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of()));

        verify(marketMapCategoryRepository, never()).saveAll(Mockito.anyList());
        verify(marketMapStockCategoryRepository, never()).saveAll(Mockito.anyList());
    }

    @Test
    void getCategories_카테고리_목록을_반환한다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1);
        MarketMapCategory chemical = category(2L, null, "화학", 2);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, chemical));

        List<CategoryItem> items = service.getCategories();

        assertThat(items).extracting(CategoryItem::name).containsExactlyInAnyOrder("반도체", "화학");
    }

    @Test
    void deletePreview_배정된_종목이_있으면_종목_목록과_함께_차단된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1);
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
        MarketMapCategory electronics = category(1L, null, "전기/전자", 1);
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
    void delete_배정된_종목이_있으면_409로_차단된다() {
        MarketMapCategory semiconductor = category(1L, null, "반도체", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(1L)))
                .thenReturn(List.of(MarketMapStockCategory.create("005930", 1L)));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_성공하면_대상과_하위카테고리가_삭제되고_남은_형제_순서가_당겨진다() {
        MarketMapCategory parent = category(1L, null, "부모", 1);
        MarketMapCategory a = category(2L, 1L, "A", 1);
        MarketMapCategory target = category(3L, 1L, "삭제대상", 2);
        MarketMapCategory b = category(4L, 1L, "B", 3);
        MarketMapCategory child = category(5L, 3L, "자식", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parent, a, target, b, child));
        when(marketMapStockCategoryRepository.findByCategoryIdIn(List.of(3L, 5L))).thenReturn(List.of());

        service.delete(3L);

        ArgumentCaptor<List<MarketMapCategory>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(target, child);
        assertThat(a.getDisplayOrder()).isEqualTo(1); // 삭제 대상보다 앞이라 영향 없음
        assertThat(b.getDisplayOrder()).isEqualTo(2); // 한 칸 당겨짐
    }

    @Test
    void reparent_기존_부모_밑_형제_순서가_당겨지고_새_부모_밑_맨_앞에_삽입된다() {
        MarketMapCategory parentA = category(1L, null, "A", 1);
        MarketMapCategory parentB = category(2L, null, "B", 2);
        MarketMapCategory x = category(3L, 1L, "X", 1);
        MarketMapCategory y = category(4L, 1L, "Y", 2);
        MarketMapCategory z = category(5L, 2L, "Z", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parentA, parentB, x, y, z));
        when(marketMapCategoryRepository.findByParentId(2L)).thenReturn(List.of(z));

        service.reparent(4L, 2L);

        assertThat(y.getParentId()).isEqualTo(2L);
        assertThat(y.getDisplayOrder()).isEqualTo(1);
        assertThat(y.getDepth()).isEqualTo(1);
        assertThat(x.getDisplayOrder()).isEqualTo(1); // Y보다 앞이라 영향 없음
        assertThat(z.getDisplayOrder()).isEqualTo(2); // 새 부모 밑에서 한 칸 밀림
    }

    @Test
    void reparent_이동한_카테고리의_하위_카테고리도_depth가_함께_바뀐다() {
        MarketMapCategory e = category(1L, null, "E", 1);
        MarketMapCategory d = category(2L, 1L, "D", 1);
        MarketMapCategory a = category(3L, null, "A", 2);
        MarketMapCategory b = category(4L, 3L, "B", 1);
        MarketMapCategory c = category(5L, 4L, "C", 1);
        ReflectionTestUtils.setField(c, "depth", 2); // 실제 부모(B)는 depth 1인데, 헬퍼는 placeholder를 항상 depth 0으로 가정하므로 보정
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(e, d, a, b, c));

        service.reparent(4L, 2L);

        assertThat(b.getParentId()).isEqualTo(2L);
        assertThat(b.getDepth()).isEqualTo(2);
        assertThat(c.getDepth()).isEqualTo(3);
    }

    @Test
    void reparent_새_부모가_자기_자신이면_409를_던진다() {
        MarketMapCategory a = category(1L, null, "A", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reparent(1L, 1L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reparent_새_부모가_자신의_하위_카테고리면_409를_던진다() {
        MarketMapCategory a = category(1L, null, "A", 1);
        MarketMapCategory b = category(2L, 1L, "B", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reparent(1L, 2L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reparent_존재하지_않는_카테고리면_404를_던진다() {
        MarketMapCategory a = category(1L, null, "A", 1);
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reparent(99L, 1L)).isInstanceOf(ResponseStatusException.class);
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
