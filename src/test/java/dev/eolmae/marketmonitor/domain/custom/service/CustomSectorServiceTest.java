package dev.eolmae.marketmonitor.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import dev.eolmae.marketmonitor.domain.custom.dto.SectorDeletePreview;
import dev.eolmae.marketmonitor.domain.custom.dto.SectorItem;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.StockInfoRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class CustomSectorServiceTest {

    private final CustomSectorRepository marketMapCategoryRepository = Mockito.mock(CustomSectorRepository.class);
    private final CustomStockSectorRepository marketMapStockCategoryRepository =
            Mockito.mock(CustomStockSectorRepository.class);
    private final StockInfoRepository stockInfoRepository = Mockito.mock(StockInfoRepository.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final CustomSectorService service = new CustomSectorService(
            marketMapCategoryRepository, marketMapStockCategoryRepository, stockInfoRepository, stockInfoCacheService);

    @Test
    void onStockInfoSynced_없는_카테고리는_생성하고_신규종목을_배정한다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        CustomSector electronics = category(2L, null, "전기/전자");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, electronics));
        // IDENTITY 전략은 insert 시점에 즉시 id가 채워지므로, save가 그 시점을 흉내내도록 stub
        when(marketMapCategoryRepository.save(Mockito.any())).thenAnswer(invocation -> {
            CustomSector saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        service.onStockInfoSynced(
                new StockInfoSyncedEvent(List.of(new NewStock("005930", "반도체"), new NewStock("051910", "화학"))));

        ArgumentCaptor<CustomSector> categoryCaptor = ArgumentCaptor.forClass(CustomSector.class);
        verify(marketMapCategoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("화학");
        assertThat(categoryCaptor.getValue().getParentId()).isNull();
        Long chemicalId = categoryCaptor.getValue().getId();

        ArgumentCaptor<List<CustomStockSector>> assignmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapStockCategoryRepository).saveAll(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue())
                .extracting(CustomStockSector::getStockCode, CustomStockSector::getSectorId)
                .containsExactlyInAnyOrder(tuple("005930", 1L), tuple("051910", chemicalId));
    }

    @Test
    void onStockInfoSynced_전부_이미_존재하면_새로_생성하지_않고_배정만_한다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));

        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of(new NewStock("005930", "반도체"))));

        verify(marketMapCategoryRepository, never()).save(Mockito.any());

        ArgumentCaptor<List<CustomStockSector>> assignmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapStockCategoryRepository).saveAll(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue())
                .extracting(CustomStockSector::getStockCode, CustomStockSector::getSectorId)
                .containsExactly(tuple("005930", 1L));
    }

    @Test
    void onStockInfoSynced_신규종목이_없으면_아무것도_하지_않는다() {
        when(stockInfoRepository.findByActiveTrue()).thenReturn(List.of());

        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of()));

        verify(marketMapCategoryRepository, never()).save(Mockito.any());
        verify(marketMapStockCategoryRepository, never()).saveAll(Mockito.anyList());
    }

    @Test
    void onStockInfoSynced_이벤트에_없어도_배정_행이_없는_활성_일반주는_배정된다() {
        // ETF로 있다가 일반주로 전환된 종목처럼, StockInfoCollector의 이벤트(신규 종목만)엔 안 실리지만
        // custom_stock_sector엔 아직 배정 행이 없는 경우
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findAll()).thenReturn(List.of());
        StockInfo unassigned = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        when(stockInfoRepository.findByActiveTrue()).thenReturn(List.of(unassigned));

        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of()));

        ArgumentCaptor<List<CustomStockSector>> assignmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketMapStockCategoryRepository).saveAll(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue())
                .extracting(CustomStockSector::getStockCode, CustomStockSector::getSectorId)
                .containsExactly(tuple("005930", 1L));
    }

    @Test
    void onStockInfoSynced_이미_배정된_종목은_다시_배정하지_않는다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findAll()).thenReturn(List.of(CustomStockSector.create("005930", 1L)));
        StockInfo assigned = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        when(stockInfoRepository.findByActiveTrue()).thenReturn(List.of(assigned));

        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of()));

        verify(marketMapStockCategoryRepository, never()).saveAll(Mockito.anyList());
    }

    @Test
    void onStockInfoSynced_ETF는_배정_대상에서_제외된다() {
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of());
        when(marketMapStockCategoryRepository.findAll()).thenReturn(List.of());
        StockInfo etf = StockInfo.create("069500", "KODEX 200", Market.KOSPI, "8", "ETF", 100L, BigDecimal.TEN);
        when(stockInfoRepository.findByActiveTrue()).thenReturn(List.of(etf));

        service.onStockInfoSynced(new StockInfoSyncedEvent(List.of()));

        verify(marketMapCategoryRepository, never()).save(Mockito.any());
        verify(marketMapStockCategoryRepository, never()).saveAll(Mockito.anyList());
    }

    @Test
    void getCategories_카테고리_목록을_반환한다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        CustomSector chemical = category(2L, null, "화학");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, chemical));

        List<SectorItem> items = service.getCategories();

        assertThat(items).extracting(SectorItem::name).containsExactlyInAnyOrder("반도체", "화학");
    }

    @Test
    void deletePreview_배정된_종목이_있으면_종목_목록과_함께_차단된다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(1L)))
                .thenReturn(List.of(CustomStockSector.create("005930", 1L)));
        StockInfo samsung = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", samsung));

        SectorDeletePreview preview = service.deletePreview(1L);

        assertThat(preview.categoryName()).isEqualTo("반도체");
        assertThat(preview.deletable()).isFalse();
        assertThat(preview.blockingStocks())
                .extracting("categoryName", "stockName")
                .containsExactly(tuple("반도체", "삼성전자"));
    }

    // 리뷰에서 지적된 구멍 — 기존 163행 테스트는 활성 종목 하나뿐이라 필터를 걸든 안 걸든 결과가 같다.
    // 활성·비활성이 섞인 경우로 blockingStocks가 실제로 필터링되는지 확인한다.
    @Test
    void deletePreview_차단_목록에는_활성_주권_종목만_담긴다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(1L)))
                .thenReturn(List.of(CustomStockSector.create("005930", 1L), CustomStockSector.create("000660", 1L)));
        StockInfo delisted = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        delisted.markInactive();
        StockInfo active = StockInfo.create("000660", "SK하이닉스", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", delisted, "000660", active));

        SectorDeletePreview preview = service.deletePreview(1L);

        assertThat(preview.deletable()).isFalse();
        assertThat(preview.blockingStocks())
                .extracting("stockCode", "stockName")
                .containsExactly(tuple("000660", "SK하이닉스"));
    }

    @Test
    void deletePreview_배정된_종목이_없으면_삭제_가능하고_하위카테고리_목록을_반환한다() {
        CustomSector electronics = category(1L, null, "전기/전자");
        CustomSector semiconductor = category(2L, 1L, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(electronics, semiconductor));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(1L, 2L))).thenReturn(List.of());

        SectorDeletePreview preview = service.deletePreview(1L);

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
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(1L)))
                .thenReturn(List.of(CustomStockSector.create("005930", 1L)));
        StockInfo samsung = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", samsung));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);
    }

    // 이번 버그의 재현 — 배정된 종목이 상장폐지 등으로 비활성이 되면 화면(종목 관리 페이지)에는 안
    // 보이는데, 이 종목 때문에 삭제가 막혀서는 안 된다.
    @Test
    void delete_비활성_종목만_배정된_카테고리는_삭제된다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(1L)))
                .thenReturn(List.of(CustomStockSector.create("005930", 1L)));
        StockInfo delisted = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        delisted.markInactive();
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", delisted));

        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();

        verify(marketMapStockCategoryRepository).deleteBySectorIdIn(List.of(1L));
        verify(marketMapCategoryRepository).deleteAll(List.of(semiconductor));
    }

    // 캐시 miss와 별개로, stock_info에 있지만 주권(코스피/코스닥)이 아닌 종목(ETF 등)도 판정 기준
    // (isActiveAndOrdinary)에 걸려 막지 않아야 한다. 지금 운영에 사례는 없지만 나중을 위해 남겨둔다.
    @Test
    void delete_주권이_아닌_종목만_배정된_카테고리는_삭제된다() {
        CustomSector etfCategory = category(1L, null, "ETF");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(etfCategory));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(1L)))
                .thenReturn(List.of(CustomStockSector.create("069500", 1L)));
        StockInfo etf = StockInfo.create("069500", "KODEX 200", Market.KOSPI, "8", "ETF", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("069500", etf));

        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();

        verify(marketMapStockCategoryRepository).deleteBySectorIdIn(List.of(1L));
    }

    // FK 위반을 막는 순서 — 이 레포는 DB 테스트가 없으므로(docs/rules/testing.md) 여기서 확인 가능한
    // 것은 서비스의 호출 순서까지다. 실제 SQL 순서는 Hibernate ActionQueue가 정하고, FK 위반 여부는
    // 배포 후에만 확인된다(지시서 5-2/8절).
    @Test
    void delete_비활성_배정_행이_카테고리보다_먼저_삭제된다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(1L)))
                .thenReturn(List.of(CustomStockSector.create("005930", 1L)));
        StockInfo delisted = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        delisted.markInactive();
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", delisted));

        service.delete(1L);

        InOrder inOrder = Mockito.inOrder(marketMapStockCategoryRepository, marketMapCategoryRepository);
        inOrder.verify(marketMapStockCategoryRepository).deleteBySectorIdIn(List.of(1L));
        inOrder.verify(marketMapCategoryRepository).deleteAll(Mockito.anyList());
    }

    // 과잉 수정 방지 — 활성 주권 종목이 하나라도 섞여 있으면 나머지가 전부 비활성이어도 여전히 막힌다.
    @Test
    void delete_활성_주권_종목이_하나라도_있으면_여전히_차단된다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(1L)))
                .thenReturn(List.of(CustomStockSector.create("005930", 1L), CustomStockSector.create("000660", 1L)));
        StockInfo delisted = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        delisted.markInactive();
        StockInfo active = StockInfo.create("000660", "SK하이닉스", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN);
        when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930", delisted, "000660", active));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);

        verify(marketMapStockCategoryRepository, never()).deleteBySectorIdIn(Mockito.anyList());
    }

    @Test
    void delete_성공하면_대상과_하위카테고리가_삭제된다() {
        CustomSector parent = category(1L, null, "부모");
        CustomSector a = category(2L, 1L, "A");
        CustomSector target = category(3L, 1L, "삭제대상");
        CustomSector b = category(4L, 1L, "B");
        CustomSector child = category(5L, 3L, "자식");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parent, a, target, b, child));
        when(marketMapStockCategoryRepository.findBySectorIdIn(List.of(3L, 5L))).thenReturn(List.of());

        service.delete(3L);

        ArgumentCaptor<List<CustomSector>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketMapCategoryRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(target, child);
    }

    @Test
    void reparent_부모와_depth가_바뀐다() {
        CustomSector parentA = category(1L, null, "A");
        CustomSector parentB = category(2L, null, "B");
        CustomSector x = category(3L, 1L, "X");
        CustomSector y = category(4L, 1L, "Y");
        CustomSector z = category(5L, 2L, "Z");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parentA, parentB, x, y, z));

        service.reparent(4L, 2L);

        assertThat(y.getParentId()).isEqualTo(2L);
        assertThat(y.getDepth()).isEqualTo(1);
    }

    @Test
    void reparent_새_부모가_null이면_최상위로_이동한다() {
        CustomSector parent = category(1L, null, "부모");
        CustomSector child = category(2L, 1L, "자식");
        CustomSector grandchild = category(3L, 2L, "손자");
        ReflectionTestUtils.setField(
                grandchild, "depth", 2); // 실제 부모(자식)는 depth 1인데, 헬퍼는 placeholder를 항상 depth 0으로 가정하므로 보정
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(parent, child, grandchild));

        service.reparent(2L, null);

        assertThat(child.getParentId()).isNull();
        assertThat(child.getDepth()).isEqualTo(0);
        assertThat(grandchild.getDepth()).isEqualTo(1);
    }

    @Test
    void reparent_이동한_카테고리의_하위_카테고리도_depth가_함께_바뀐다() {
        CustomSector e = category(1L, null, "E");
        CustomSector d = category(2L, 1L, "D");
        CustomSector a = category(3L, null, "A");
        CustomSector b = category(4L, 3L, "B");
        CustomSector c = category(5L, 4L, "C");
        ReflectionTestUtils.setField(c, "depth", 2); // 실제 부모(B)는 depth 1인데, 헬퍼는 placeholder를 항상 depth 0으로 가정하므로 보정
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(e, d, a, b, c));

        service.reparent(4L, 2L);

        assertThat(b.getParentId()).isEqualTo(2L);
        assertThat(b.getDepth()).isEqualTo(2);
        assertThat(c.getDepth()).isEqualTo(3);
    }

    @Test
    void reparent_새_부모가_자기_자신이면_409를_던진다() {
        CustomSector a = category(1L, null, "A");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reparent(1L, 1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void reparent_새_부모가_자신의_하위_카테고리면_409를_던진다() {
        CustomSector a = category(1L, null, "A");
        CustomSector b = category(2L, 1L, "B");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reparent(1L, 2L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void exclude_카테고리를_제외_상태로_바꾼다() {
        CustomSector semiconductor = category(1L, null, "반도체");
        when(marketMapCategoryRepository.findById(1L)).thenReturn(Optional.of(semiconductor));

        service.exclude(1L);

        assertThat(semiconductor.isExcluded()).isTrue();
    }

    @Test
    void include_카테고리를_제외_해제한다() {
        CustomSector semiconductor = category(1L, null, "반도체");
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
        CustomSector semiconductor = category(1L, null, "반도체");
        CustomSector chemical = category(2L, null, "화학");
        semiconductor.exclude();
        chemical.exclude();
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(semiconductor, chemical));

        service.resetExcludes();

        assertThat(semiconductor.isExcluded()).isFalse();
        assertThat(chemical.isExcluded()).isFalse();
    }

    @Test
    void reparent_존재하지_않는_카테고리면_404를_던진다() {
        CustomSector a = category(1L, null, "A");
        when(marketMapCategoryRepository.findAll()).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reparent(99L, 1L)).isInstanceOf(NotFoundException.class);
    }

    private CustomSector category(Long id, Long parentId, String name) {
        CustomSector category = parentId == null ? CustomSector.createParent(name) : categoryWithParent(parentId, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private CustomSector categoryWithParent(Long parentId, String name) {
        CustomSector parent = CustomSector.createParent("parent-placeholder");
        ReflectionTestUtils.setField(parent, "id", parentId);
        return CustomSector.createChild(name, parent);
    }
}
