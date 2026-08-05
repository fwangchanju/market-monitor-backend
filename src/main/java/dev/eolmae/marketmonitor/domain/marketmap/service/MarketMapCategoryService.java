package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryDeletePreview;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryItem;
import dev.eolmae.marketmonitor.domain.marketmap.dto.StockCategoryItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 카테고리 추가/삭제/순서 변경. 항상 라이브(현재 표시 중인) 트리만을 대상으로 한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketMapCategoryService {

    private static final int INITIAL_DISPLAY_ORDER = 1;
    private static final long ROOT_KEY = 0L;
    private static final int SHIFT_FORWARD = 1;
    private static final int SHIFT_BACKWARD = -1;

    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final StockInfoCacheService stockInfoCacheService;

    @Transactional(readOnly = true)
    public List<CategoryItem> getCategories() {
        return findAllCategories().stream().map(this::toItem).toList();
    }

    /** stock_info 카테고리명 중 아직 없는 것만 최상위 카테고리로 생성. stock -> marketmap 순환 의존을 피하려고 이벤트로 수신(StockInfoSyncedEvent 참고). */
    @EventListener
    public void onStockInfoSynced(StockInfoSyncedEvent event) {
        syncCategories(event.categoryNames());
    }

    /** 사용자가 직접 만드는 카테고리는 최상단에 추가되지만(createParent), 자동 생성은 최하단에 추가.
     * 백그라운드 동기화로 인해 사용자가 정리해둔 기존 순서를 건드리지 않기 위함. */
    private void syncCategories(Set<String> categoryNames) {
        Map<String, MarketMapCategory> existingByName = new HashMap<>();
        int maxOrder = 0;
        for (MarketMapCategory category : findAllCategories()) {
            existingByName.put(category.getName(), category);
            if (category.getParentId() == null && category.getDisplayOrder() > maxOrder) {
                maxOrder = category.getDisplayOrder();
            }
        }

        List<MarketMapCategory> newCategories = new ArrayList<>();
        for (String categoryName : categoryNames) {
            MarketMapCategory existing = existingByName.get(categoryName);
            if (existing != null) {
                if (existing.isUnlocked()) {
                    existing.lock();
                }
                continue;
            }
            newCategories.add(MarketMapCategory.createParent(categoryName, ++maxOrder, true));
        }
        marketMapCategoryRepository.saveAll(newCategories);
    }

    public CategoryItem createParent(String name) {
        if (marketMapCategoryRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        // 최상위 카테고리는 부모 카테고리가 없다.
        shiftSiblingsForInsert(findCategoriesByParentId(null));
        MarketMapCategory category = MarketMapCategory.createParent(name, INITIAL_DISPLAY_ORDER, false);
        return toItem(marketMapCategoryRepository.save(category));
    }

    public CategoryItem createChild(String name, Long parentId) {
        if (marketMapCategoryRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        MarketMapCategory parent = marketMapCategoryRepository
                .findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        shiftSiblingsForInsert(findCategoriesByParentId(parentId));
        MarketMapCategory category = MarketMapCategory.createChild(name, parent, INITIAL_DISPLAY_ORDER);
        return toItem(marketMapCategoryRepository.save(category));
    }

    private void shiftSiblingsForInsert(List<MarketMapCategory> siblings) {
        siblings.forEach(sibling -> sibling.reorder(sibling.getDisplayOrder() + SHIFT_FORWARD));
    }

    private List<MarketMapCategory> findCategoriesByParentId(Long parentId) {
        return marketMapCategoryRepository.findByParentId(parentId);
    }

    public void reorder(Long categoryId, int newDisplayOrder) {
        MarketMapCategory target = marketMapCategoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int oldDisplayOrder = target.getDisplayOrder();
        List<MarketMapCategory> siblings = findCategoriesByParentId(target.getParentId());

        // 카테고리를 앞으로 옮기면 그 사이에 있던 카테고리들이 한 칸씩 뒤로 밀리고,
        // 뒤로 옮기면 그 사이에 있던 카테고리들이 한 칸씩 앞으로 밀린다.
        int startOrder = Math.min(oldDisplayOrder, newDisplayOrder);
        int endOrder = Math.max(oldDisplayOrder, newDisplayOrder);
        int shiftDirection = oldDisplayOrder == startOrder ? SHIFT_BACKWARD : SHIFT_FORWARD;

        shiftDisplayOrders(siblings, categoryId, startOrder, endOrder, shiftDirection);
        target.reorder(newDisplayOrder);
    }

    private void shiftDisplayOrders(List<MarketMapCategory> siblings, Long excludeId, int startOrder, int endOrder, int direction) {
        siblings.stream()
                .filter(category -> !category.getId().equals(excludeId))
                .filter(category -> startOrder <= category.getDisplayOrder() && category.getDisplayOrder() <= endOrder)
                .forEach(sibling -> sibling.reorder(sibling.getDisplayOrder() + direction));
    }

    /** 카테고리가 형제 그룹에서 빠져나간 뒤 남은 형제들의 순서를 당긴다(startOrder 이후 전부, 상한 없음). */
    private void shiftDisplayOrdersBackward(List<MarketMapCategory> siblings, Long excludeId, int startOrder) {
        shiftDisplayOrders(siblings, excludeId, startOrder, Integer.MAX_VALUE, SHIFT_BACKWARD);
    }

    /** categoryId를 newParentId의 자식으로 옮긴다. newParentId가 categoryId 자신이거나 그 하위 카테고리면 순환 구조가
     * 되므로 409로 막는다. 기존 부모 밑의 형제들은 순서를 한 칸씩 당기고, 새 부모 밑에서는 맨 앞에 삽입된다.
     * 하위 카테고리 전체는 depth 변화량만큼 함께 갱신한다. */
    public void reparent(Long categoryId, Long newParentId) {
        CategoryMaps maps = getCategoryMaps();
        Map<Long, MarketMapCategory> categoryById = maps.categoryById();
        Map<Long, List<MarketMapCategory>> categoryByParentId = maps.categoryByParentId();
        MarketMapCategory target = findCategory(categoryId, categoryById);
        MarketMapCategory newParent = findCategory(newParentId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, categoryByParentId);
        if (subCategoryIds.contains(newParentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        long targetParentKey = resolveParentKey(target);
        shiftDisplayOrdersBackward(
                categoryByParentId.getOrDefault(targetParentKey, List.of()), target.getId(), target.getDisplayOrder() + 1);

        int depthDifference = (newParent.getDepth() + 1) - target.getDepth();
        shiftSiblingsForInsert(categoryByParentId.getOrDefault(newParentId, List.of()));
        target.changeParent(newParentId, INITIAL_DISPLAY_ORDER);

        // target을 포함한 하위 카테고리 전체 depth를 depthDifference만큼 일괄 이동
        if (depthDifference != 0) {
            subCategoryIds.stream()
                    .map(categoryById::get)
                    .forEach(category -> category.changeDepth(category.getDepth() + depthDifference));
        }
    }

    private long resolveParentKey(MarketMapCategory category) {
        return category.getParentId() == null ? ROOT_KEY : category.getParentId();
    }

    @Transactional(readOnly = true)
    public CategoryDeletePreview deletePreview(Long categoryId) {
        CategoryMaps maps = getCategoryMaps();
        Map<Long, MarketMapCategory> categoryById = maps.categoryById();
        MarketMapCategory target = findCategory(categoryId, categoryById);
        if (target.isLocked()) {
            return CategoryDeletePreview.blocked(target.getName(), List.of());
        }

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, maps.categoryByParentId());
        List<MarketMapStockCategory> stockCategories = marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds);
        if (!stockCategories.isEmpty()) {
            return CategoryDeletePreview.blocked(target.getName(), toBlockingStockCategoryItems(stockCategories, categoryById));
        }
        return CategoryDeletePreview.deletable(target.getName(), toDeletableCategoryNames(categoryId, subCategoryIds, categoryById));
    }

    public void delete(Long categoryId) {
        CategoryMaps maps = getCategoryMaps();
        Map<Long, MarketMapCategory> categoryById = maps.categoryById();
        Map<Long, List<MarketMapCategory>> categoryByParentId = maps.categoryByParentId();
        MarketMapCategory target = findCategory(categoryId, categoryById);
        if (target.isLocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, categoryByParentId);
        if (!marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        List<MarketMapCategory> subCategories = subCategoryIds.stream()
                .map(categoryById::get)
                .sorted(Comparator.comparingInt(MarketMapCategory::getDepth).reversed())
                .toList();
        marketMapCategoryRepository.deleteAll(subCategories);

        shiftDisplayOrdersBackward(
                categoryByParentId.getOrDefault(resolveParentKey(target), List.of()), target.getId(), target.getDisplayOrder() + 1);
    }

    /** 카테고리 전체 스냅샷을 id 조회용/parentId 그룹핑용 두 가지 형태로 함께 준비해둔다.
     * reparent/deletePreview/delete처럼 둘 다 필요한 경우에만 사용. */
    private record CategoryMaps(
            Map<Long, MarketMapCategory> categoryById, Map<Long, List<MarketMapCategory>> categoryByParentId) {}

    private CategoryMaps getCategoryMaps() {
        List<MarketMapCategory> categories = findAllCategories();
        Map<Long, MarketMapCategory> categoryById = new HashMap<>();
        Map<Long, List<MarketMapCategory>> categoryByParentId = new HashMap<>();
        for (MarketMapCategory category : categories) {
            categoryById.put(category.getId(), category);
            categoryByParentId.computeIfAbsent(resolveParentKey(category), key -> new ArrayList<>()).add(category);
        }
        return new CategoryMaps(categoryById, categoryByParentId);
    }

    private List<MarketMapCategory> findAllCategories() {
        return marketMapCategoryRepository.findAll();
    }

    private MarketMapCategory findCategory(Long categoryId, Map<Long, MarketMapCategory> categoryById) {
        MarketMapCategory category = categoryById.get(categoryId);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return category;
    }

    private List<String> toDeletableCategoryNames(Long categoryId, List<Long> subCategoryIds, Map<Long, MarketMapCategory> categoryById) {
        return subCategoryIds.stream()
                .filter(id -> !id.equals(categoryId))
                .map(id -> categoryById.get(id).getName())
                .toList();
    }

    private List<StockCategoryItem> toBlockingStockCategoryItems(
            List<MarketMapStockCategory> stockCategories, Map<Long, MarketMapCategory> categoryById) {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return stockCategories.stream()
                .map(stockCategory -> new StockCategoryItem(
                        stockCategory.getStockCode(),
                        stockInfoCache.get(stockCategory.getStockCode()).getStockName(),
                        categoryById.get(stockCategory.getCategoryId()).getName()))
                .toList();
    }

    private List<Long> collectSubCategoryIds(Long categoryId, Map<Long, List<MarketMapCategory>> categoryByParentId) {
        List<Long> collectedIds = new ArrayList<>();
        collectedIds.add(categoryId);
        collectSubCategories(categoryId, categoryByParentId, collectedIds);
        return collectedIds;
    }

    private void collectSubCategories(
            Long parentId, Map<Long, List<MarketMapCategory>> categoryByParentId, List<Long> collectedIds) {
        for (MarketMapCategory child : categoryByParentId.getOrDefault(parentId, List.of())) {
            collectedIds.add(child.getId());
            collectSubCategories(child.getId(), categoryByParentId, collectedIds);
        }
    }

    private CategoryItem toItem(MarketMapCategory category) {
        return new CategoryItem(
                category.getId(),
                category.getName(),
                category.getParentId(),
                category.getDepth(),
                category.getDisplayOrder(),
                category.isLocked());
    }
}
