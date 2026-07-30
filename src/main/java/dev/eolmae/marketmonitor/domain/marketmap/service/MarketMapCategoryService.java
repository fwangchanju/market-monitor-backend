package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.domain.marketmap.dto.BlockingStockItem;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryDeletePreview;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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

    public CategoryItem createParent(String name) {
        // 최상위 카테고리는 부모 카테고리가 없다.
        shiftSiblingsForInsert(null);
        MarketMapCategory category = MarketMapCategory.createParent(name, INITIAL_DISPLAY_ORDER);
        return toItem(marketMapCategoryRepository.save(category));
    }

    public CategoryItem createChild(String name, Long parentId) {
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
        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, categories);

        List<MarketMapStockCategory> stockCategories = marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds);
        if (!stockCategories.isEmpty()) {
            return CategoryDeletePreview.blocked(toBlockingStockItems(stockCategories, categories));
        }
        return CategoryDeletePreview.deletable(toDeletableCategoryNames(categoryId, subCategoryIds, categories));
    }

    public void delete(Long categoryId) {
        List<MarketMapCategory> categories = marketMapCategoryRepository.findAll();
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

    private List<String> toDeletableCategoryNames(Long categoryId, List<Long> subCategoryIds, List<MarketMapCategory> categories) {
        Map<Long, String> categoryNameById = categories.stream().collect(Collectors.toMap(MarketMapCategory::getId, MarketMapCategory::getName));
        return subCategoryIds.stream().filter(id -> !id.equals(categoryId)).map(categoryNameById::get).toList();
    }

    private List<BlockingStockItem> toBlockingStockItems(List<MarketMapStockCategory> stockCategories, List<MarketMapCategory> categories) {
        Map<Long, String> categoryNameById = categories.stream().collect(Collectors.toMap(MarketMapCategory::getId, MarketMapCategory::getName));
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return stockCategories.stream()
                .map(stockCategory -> new BlockingStockItem(
                        categoryNameById.get(stockCategory.getCategoryId()),
                        stockInfoCache.get(stockCategory.getStockCode()).getStockName()))
                .toList();
    }

    private List<Long> collectSubCategoryIds(Long categoryId, List<MarketMapCategory> categories) {
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        Map<Long, List<MarketMapCategory>> childrenByParentId = categories.stream()
                .collect(Collectors.groupingBy(category -> category.getParentId() == null ? ROOT_KEY : category.getParentId()));
        collectSubCategories(categoryId, childrenByParentId, ids);
        return ids;
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
                category.getId(), category.getName(), category.getParentId(), category.getDepth(), category.getDisplayOrder());
    }
}
