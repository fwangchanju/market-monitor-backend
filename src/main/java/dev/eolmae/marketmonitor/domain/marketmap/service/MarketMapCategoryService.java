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
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 카테고리 추가/삭제/순서 변경. 항상 라이브(현재 표시 중인) 트리만을 대상으로 한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class MarketMapCategoryService {

    private static final int INITIAL_DISPLAY_ORDER = 1;
    private static final Long ROOT_KEY = 0L;

    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final StockInfoCacheService stockInfoCacheService;

    @Transactional(readOnly = true)
    public List<CategoryItem> getCategories() {
        return marketMapCategoryRepository.findAll().stream().map(this::toItem).toList();
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
        for (MarketMapCategory category : marketMapCategoryRepository.findAll()) {
            existingByName.put(category.getName(), category);
            if (category.getParentId() == null && category.getDisplayOrder() > maxOrder) {
                maxOrder = category.getDisplayOrder();
            }
        }

        List<MarketMapCategory> newCategories = new ArrayList<>();
        for (String categoryName : categoryNames) {
            MarketMapCategory existing = existingByName.get(categoryName);
            if (existing != null) {
                if (!existing.isSynced()) {
                    existing.markSynced();
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
        shiftSiblingsForInsert(null);
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
        shiftSiblingsForInsert(parentId);
        MarketMapCategory category = MarketMapCategory.createChild(name, parent, INITIAL_DISPLAY_ORDER);
        return toItem(marketMapCategoryRepository.save(category));
    }

    private void shiftSiblingsForInsert(Long parentId) {
        marketMapCategoryRepository
                .findByParentId(parentId)
                .forEach(sibling -> sibling.reorder(sibling.getDisplayOrder() + 1));
    }

    public void reorder(Long categoryId, int newDisplayOrder) {
        MarketMapCategory target = marketMapCategoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int oldDisplayOrder = target.getDisplayOrder();
        List<MarketMapCategory> siblings = marketMapCategoryRepository.findByParentId(target.getParentId());

        // 카테고리를 앞으로 옮기면 그 사이에 있던 카테고리들이 한 칸씩 뒤로 밀리고,
        // 뒤로 옮기면 그 사이에 있던 카테고리들이 한 칸씩 앞으로 밀린다.
        int startOrder = Math.min(oldDisplayOrder, newDisplayOrder);
        int endOrder = Math.max(oldDisplayOrder, newDisplayOrder);
        int shiftDirection = oldDisplayOrder == startOrder ? -1 : 1;

        siblings.stream()
                .filter(sibling -> !sibling.getId().equals(categoryId))
                .filter(sibling -> startOrder <= sibling.getDisplayOrder() && sibling.getDisplayOrder() <= endOrder)
                .forEach(sibling -> sibling.reorder(sibling.getDisplayOrder() + shiftDirection));
        target.reorder(newDisplayOrder);
    }

    @Transactional(readOnly = true)
    public CategoryDeletePreview deletePreview(Long categoryId) {
        List<MarketMapCategory> categories = marketMapCategoryRepository.findAll();
        MarketMapCategory target = findCategory(categoryId, categories);
        if (target.isSynced()) {
            return CategoryDeletePreview.blocked(target.getName(), List.of());
        }

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, categories);
        List<MarketMapStockCategory> stockCategories = marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds);
        if (!stockCategories.isEmpty()) {
            return CategoryDeletePreview.blocked(target.getName(), toBlockingStockCategoryItems(stockCategories, categories));
        }
        return CategoryDeletePreview.deletable(target.getName(), toDeletableCategoryNames(categoryId, subCategoryIds, categories));
    }

    public void delete(Long categoryId) {
        List<MarketMapCategory> categories = marketMapCategoryRepository.findAll();
        if (findCategory(categoryId, categories).isSynced()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, categories);
        if (!marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        List<MarketMapCategory> subCategories = categories.stream()
                .filter(category -> subCategoryIds.contains(category.getId()))
                .sorted(Comparator.comparingInt(MarketMapCategory::getDepth).reversed())
                .toList();
        marketMapCategoryRepository.deleteAll(subCategories);
    }

    private MarketMapCategory findCategory(Long categoryId, List<MarketMapCategory> categories) {
        return categories.stream()
                .filter(category -> category.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private List<String> toDeletableCategoryNames(Long categoryId, List<Long> subCategoryIds, List<MarketMapCategory> categories) {
        Map<Long, MarketMapCategory> categoryById = categories.stream().collect(Collectors.toMap(MarketMapCategory::getId, Function.identity()));
        return subCategoryIds.stream()
                .filter(id -> !id.equals(categoryId))
                .map(id -> categoryById.get(id).getName())
                .toList();
    }

    private List<StockCategoryItem> toBlockingStockCategoryItems(List<MarketMapStockCategory> stockCategories, List<MarketMapCategory> categories) {
        Map<Long, MarketMapCategory> categoryById = categories.stream().collect(Collectors.toMap(MarketMapCategory::getId, Function.identity()));
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return stockCategories.stream()
                .map(stockCategory -> {
                    String stockCode = stockCategory.getStockCode();
                    return new StockCategoryItem(
                            stockCode,
                            stockInfoCache.get(stockCode).getStockName(),
                            categoryById.get(stockCategory.getCategoryId()).getName());
                })
                .toList();
    }

    private List<Long> collectSubCategoryIds(Long categoryId, List<MarketMapCategory> categories) {
        List<Long> collectedIds = new ArrayList<>();
        collectedIds.add(categoryId);
        Map<Long, List<MarketMapCategory>> childrenByParentId = categories.stream()
                .collect(Collectors.groupingBy(category -> category.getParentId() == null ? ROOT_KEY : category.getParentId()));
        collectSubCategories(categoryId, childrenByParentId, collectedIds);
        return collectedIds;
    }

    private void collectSubCategories(
            Long parentId, Map<Long, List<MarketMapCategory>> childrenByParentId, List<Long> collectedIds) {
        for (MarketMapCategory child : childrenByParentId.getOrDefault(parentId, List.of())) {
            collectedIds.add(child.getId());
            collectSubCategories(child.getId(), childrenByParentId, collectedIds);
        }
    }

    private CategoryItem toItem(MarketMapCategory category) {
        return new CategoryItem(
                category.getId(),
                category.getName(),
                category.getParentId(),
                category.getDepth(),
                category.getDisplayOrder(),
                category.isSynced());
    }
}
