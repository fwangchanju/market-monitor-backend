package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 추가/삭제/재부모화. 항상 라이브(현재 표시 중인) 트리만을 대상으로 한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketMapCategoryService {

    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final StockInfoCacheService stockInfoCacheService;

    @Transactional(readOnly = true)
    public List<CategoryItem> getCategories() {
        return findAllCategories().stream().map(this::toItem).toList();
    }

    /** 신규 종목(이벤트 발행 단계에서 이미 주권만 필터링됨) 기준으로, 카테고리명 중 아직 없는 것만 최상위 카테고리로
     * 생성한 뒤 market_map_stock_category에 배정한다.
     * stock -> marketmap 순환 의존을 피하려고 이벤트로 수신(StockInfoSyncedEvent 참고). */
    @EventListener
    public void onStockInfoSynced(StockInfoSyncedEvent event) {
        List<StockInfoSyncedEvent.NewStock> newStocks = event.newStocks();
        if (newStocks.isEmpty()) {
            return;
        }

        Set<String> categoryNames = newStocks.stream()
                .map(StockInfoSyncedEvent.NewStock::categoryName)
                .collect(Collectors.toSet());
        Map<String, MarketMapCategory> categoryByName = syncCategories(categoryNames);
        createNewStockCategories(newStocks, categoryByName);
    }

    private void createNewStockCategories(
            List<StockInfoSyncedEvent.NewStock> newStocks, Map<String, MarketMapCategory> categoryByName) {
        List<MarketMapStockCategory> newStockCategories = newStocks.stream()
                .map(stock -> MarketMapStockCategory.create(
                        stock.stockCode(),
                        categoryByName.get(stock.categoryName()).getId()))
                .toList();
        marketMapStockCategoryRepository.saveAll(newStockCategories);
    }

    /** 기존 + 신규 생성분을 합친 이름별 맵을 리턴해서, 호출부가 다시 전체 조회할 필요가 없게 한다. */
    private Map<String, MarketMapCategory> syncCategories(Set<String> categoryNames) {
        Map<String, MarketMapCategory> existingByName = new HashMap<>();
        for (MarketMapCategory category : findAllCategories()) {
            existingByName.put(category.getName(), category);
        }

        List<MarketMapCategory> newCategories = new ArrayList<>();
        for (String categoryName : categoryNames) {
            if (existingByName.containsKey(categoryName)) {
                continue;
            }
            newCategories.add(MarketMapCategory.createParent(categoryName));
        }
        marketMapCategoryRepository.saveAll(newCategories);
        newCategories.forEach(category -> existingByName.put(category.getName(), category));
        return existingByName;
    }

    public CategoryItem createParent(String name) {
        if (marketMapCategoryRepository.existsByName(name)) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_DUPLICATE, name);
        }
        MarketMapCategory category = MarketMapCategory.createParent(name);
        return toItem(marketMapCategoryRepository.save(category));
    }

    public CategoryItem createChild(String name, Long parentId) {
        if (marketMapCategoryRepository.existsByName(name)) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_DUPLICATE, name);
        }
        MarketMapCategory parent = marketMapCategoryRepository
                .findById(parentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, parentId));
        MarketMapCategory category = MarketMapCategory.createChild(name, parent);
        return toItem(marketMapCategoryRepository.save(category));
    }

    public void exclude(Long categoryId) {
        MarketMapCategory target = marketMapCategoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId));
        target.exclude();
    }

    public void include(Long categoryId) {
        MarketMapCategory target = marketMapCategoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId));
        target.include();
    }

    public void resetExcludes() {
        findAllCategories().forEach(MarketMapCategory::include);
    }

    public void rename(Long categoryId, String name) {
        MarketMapCategory target = marketMapCategoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId));
        if (target.getName().equals(name)) {
            return;
        }
        if (marketMapCategoryRepository.existsByName(name)) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_DUPLICATE, name);
        }
        target.rename(name);
    }

    /** categoryId를 newParentId의 자식으로 옮긴다. newParentId가 null이면 최상위(루트)로 옮긴다.
     * newParentId가 categoryId 자신이거나 그 하위 카테고리면 순환 구조가 되므로 409로 막는다.
     * 하위 카테고리 전체는 depth 변화량만큼 함께 갱신한다. */
    public void reparent(Long categoryId, Long newParentId) {
        CategoryMaps maps = getCategoryMaps();
        Map<Long, MarketMapCategory> categoryById = maps.categoryById();
        Map<Long, List<MarketMapCategory>> categoryByParentId = maps.categoryByParentId();
        MarketMapCategory target = findCategory(categoryId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, categoryByParentId);
        if (subCategoryIds.contains(newParentId)) {
            throw new ConflictException(ErrorCode.CATEGORY_CIRCULAR_REFERENCE, categoryId, newParentId);
        }

        int newDepth = 0;
        if (newParentId != null) {
            MarketMapCategory newParent = findCategory(newParentId, categoryById);
            newDepth = newParent.getDepth() + 1;
        }
        int depthDifference = newDepth - target.getDepth();
        target.changeParent(newParentId);

        // target을 포함한 하위 카테고리 전체 depth를 depthDifference만큼 일괄 이동
        if (depthDifference != 0) {
            subCategoryIds.stream()
                    .map(categoryById::get)
                    .forEach(category -> category.changeDepth(category.getDepth() + depthDifference));
        }
    }

    @Transactional(readOnly = true)
    public CategoryDeletePreview deletePreview(Long categoryId) {
        CategoryMaps maps = getCategoryMaps();
        Map<Long, MarketMapCategory> categoryById = maps.categoryById();
        MarketMapCategory target = findCategory(categoryId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, maps.categoryByParentId());
        List<MarketMapStockCategory> stockCategories =
                marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds);
        if (!stockCategories.isEmpty()) {
            return CategoryDeletePreview.blocked(
                    target.getName(), toBlockingStockCategoryItems(stockCategories, categoryById));
        }
        return CategoryDeletePreview.deletable(
                target.getName(), toDeletableCategoryNames(categoryId, subCategoryIds, categoryById));
    }

    public void delete(Long categoryId) {
        CategoryMaps maps = getCategoryMaps();
        Map<Long, MarketMapCategory> categoryById = maps.categoryById();
        findCategory(categoryId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, maps.categoryByParentId());
        if (!marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds).isEmpty()) {
            throw new ConflictException(ErrorCode.CATEGORY_HAS_ASSIGNED_STOCK, categoryId);
        }

        List<MarketMapCategory> subCategories = subCategoryIds.stream()
                .map(categoryById::get)
                .sorted(Comparator.comparingInt(MarketMapCategory::getDepth).reversed())
                .toList();
        marketMapCategoryRepository.deleteAll(subCategories);
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
            categoryByParentId
                    .computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
                    .add(category);
        }
        return new CategoryMaps(categoryById, categoryByParentId);
    }

    private List<MarketMapCategory> findAllCategories() {
        return marketMapCategoryRepository.findAll();
    }

    private MarketMapCategory findCategory(Long categoryId, Map<Long, MarketMapCategory> categoryById) {
        MarketMapCategory category = categoryById.get(categoryId);
        if (category == null) {
            throw new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId);
        }
        return category;
    }

    private List<String> toDeletableCategoryNames(
            Long categoryId, List<Long> subCategoryIds, Map<Long, MarketMapCategory> categoryById) {
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
        return new CategoryItem(category.getId(), category.getName(), category.getParentId(), category.getDepth());
    }
}
