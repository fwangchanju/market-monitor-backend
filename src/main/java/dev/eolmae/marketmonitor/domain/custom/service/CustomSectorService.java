package dev.eolmae.marketmonitor.domain.custom.service;

import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
import dev.eolmae.marketmonitor.domain.custom.dto.SectorDeletePreview;
import dev.eolmae.marketmonitor.domain.custom.dto.SectorItem;
import dev.eolmae.marketmonitor.domain.custom.dto.StockSectorItem;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 추가/삭제/재부모화. 항상 라이브(현재 표시 중인) 트리만을 대상으로 한다. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CustomSectorService {

    private final CustomSectorRepository customSectorRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final StockInfoCacheService stockInfoCacheService;

    @Transactional(readOnly = true)
    public List<SectorItem> getCategories() {
        return findAllCategories(CurrentUser.requireId()).stream()
                .map(this::toItem)
                .toList();
    }

    public SectorItem createParent(String name) {
        Long userId = CurrentUser.requireId();
        if (customSectorRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_DUPLICATE, name);
        }
        CustomSector category = CustomSector.createParent(userId, name);
        return toItem(customSectorRepository.save(category));
    }

    public SectorItem createChild(String name, Long parentId) {
        Long userId = CurrentUser.requireId();
        if (customSectorRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_DUPLICATE, name);
        }
        CustomSector parent = customSectorRepository
                .findByIdAndUserId(parentId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, parentId));
        CustomSector category = CustomSector.createChild(userId, name, parent);
        return toItem(customSectorRepository.save(category));
    }

    public void exclude(Long categoryId) {
        CustomSector target = findOwnedCategory(categoryId, CurrentUser.requireId());
        target.exclude();
    }

    public void include(Long categoryId) {
        CustomSector target = findOwnedCategory(categoryId, CurrentUser.requireId());
        target.include();
    }

    public void resetExcludes() {
        findAllCategories(CurrentUser.requireId()).forEach(CustomSector::include);
    }

    public void rename(Long categoryId, String name) {
        Long userId = CurrentUser.requireId();
        CustomSector target = findOwnedCategory(categoryId, userId);
        if (target.getName().equals(name)) {
            return;
        }
        if (customSectorRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_DUPLICATE, name);
        }
        target.rename(name);
    }

    /** categoryId를 newParentId의 자식으로 옮긴다. newParentId가 null이면 최상위(루트)로 옮긴다.
     * newParentId가 categoryId 자신이거나 그 하위 카테고리면 순환 구조가 되므로 409로 막는다.
     * 하위 카테고리 전체는 depth 변화량만큼 함께 갱신한다. */
    public void reparent(Long categoryId, Long newParentId) {
        CategoryMaps maps = getCategoryMaps();
        Map<Long, CustomSector> categoryById = maps.categoryById();
        Map<Long, List<CustomSector>> categoryByParentId = maps.categoryByParentId();
        CustomSector target = findCategory(categoryId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, categoryByParentId);
        if (subCategoryIds.contains(newParentId)) {
            throw new ConflictException(ErrorCode.CATEGORY_CIRCULAR_REFERENCE, categoryId, newParentId);
        }

        int newDepth = 0;
        if (newParentId != null) {
            CustomSector newParent = findCategory(newParentId, categoryById);
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
    public SectorDeletePreview deletePreview(Long categoryId) {
        Long userId = CurrentUser.requireId();
        CategoryMaps maps = getCategoryMaps();
        Map<Long, CustomSector> categoryById = maps.categoryById();
        CustomSector target = findCategory(categoryId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, maps.categoryByParentId());
        List<CustomStockSector> stockCategories =
                customStockSectorRepository.findByUserIdAndSectorIdIn(userId, subCategoryIds);
        List<CustomStockSector> blockingStockCategories = findBlockingStockCategories(stockCategories);
        if (!blockingStockCategories.isEmpty()) {
            return SectorDeletePreview.blocked(
                    target.getName(), toBlockingStockCategoryItems(blockingStockCategories, categoryById));
        }
        return SectorDeletePreview.deletable(
                target.getName(), toDeletableCategoryNames(categoryId, subCategoryIds, categoryById));
    }

    public void delete(Long categoryId) {
        Long userId = CurrentUser.requireId();
        CategoryMaps maps = getCategoryMaps();
        Map<Long, CustomSector> categoryById = maps.categoryById();
        findCategory(categoryId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, maps.categoryByParentId());
        List<CustomStockSector> stockCategories =
                customStockSectorRepository.findByUserIdAndSectorIdIn(userId, subCategoryIds);
        if (!findBlockingStockCategories(stockCategories).isEmpty()) {
            throw new ConflictException(ErrorCode.CATEGORY_HAS_ASSIGNED_STOCK, categoryId);
        }

        // 활성 주권 배정은 없다고 확인했지만(위 판정), 비활성 종목의 배정 행은 여전히 남아있을 수 있다 —
        // custom_sector를 가리키는 FK라 카테고리 삭제 전에 먼저 지워야 한다(결정 1).
        if (!stockCategories.isEmpty()) {
            log.info("[카테고리삭제] 비활성 배정 행 삭제 | categoryId={}|count={}", categoryId, stockCategories.size());
            customStockSectorRepository.deleteByUserIdAndSectorIdIn(userId, subCategoryIds);
        }

        List<CustomSector> subCategories = subCategoryIds.stream()
                .map(categoryById::get)
                .sorted(Comparator.comparingInt(CustomSector::getDepth).reversed())
                .toList();
        customSectorRepository.deleteAll(subCategories);
    }

    /** 카테고리 전체 스냅샷을 id 조회용/parentId 그룹핑용 두 가지 형태로 함께 준비해둔다.
     * reparent/deletePreview/delete처럼 둘 다 필요한 경우에만 사용. */
    private record CategoryMaps(
            Map<Long, CustomSector> categoryById, Map<Long, List<CustomSector>> categoryByParentId) {}

    private CategoryMaps getCategoryMaps() {
        List<CustomSector> categories = findAllCategories(CurrentUser.requireId());
        Map<Long, CustomSector> categoryById = new HashMap<>();
        Map<Long, List<CustomSector>> categoryByParentId = new HashMap<>();
        for (CustomSector category : categories) {
            categoryById.put(category.getId(), category);
            categoryByParentId
                    .computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
                    .add(category);
        }
        return new CategoryMaps(categoryById, categoryByParentId);
    }

    private List<CustomSector> findAllCategories(Long userId) {
        return customSectorRepository.findAllByUserId(userId);
    }

    private CustomSector findOwnedCategory(Long categoryId, Long userId) {
        return customSectorRepository
                .findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId));
    }

    private CustomSector findCategory(Long categoryId, Map<Long, CustomSector> categoryById) {
        CustomSector category = categoryById.get(categoryId);
        if (category == null) {
            throw new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId);
        }
        return category;
    }

    private List<String> toDeletableCategoryNames(
            Long categoryId, List<Long> subCategoryIds, Map<Long, CustomSector> categoryById) {
        return subCategoryIds.stream()
                .filter(id -> !id.equals(categoryId))
                .map(id -> categoryById.get(id).getName())
                .toList();
    }

    /** "이 카테고리들을 막는" 배정만 남긴다 — 그 행이 가리키는 종목이 stock_info 캐시에서 활성 주권인
     * 것만(캐시에 아예 없는 종목은 화면에도 안 보이므로 "막지 않음"으로 친다, 5-3). deletePreview·delete가
     * 이 필터를 공유해서 화면(종목 관리 페이지)과 같은 기준으로 판정한다. */
    private List<CustomStockSector> findBlockingStockCategories(List<CustomStockSector> stockCategories) {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return stockCategories.stream()
                .filter(stockCategory -> isActiveOrdinaryStock(stockCategory, stockInfoCache))
                .toList();
    }

    private boolean isActiveOrdinaryStock(CustomStockSector stockCategory, Map<String, StockInfo> stockInfoCache) {
        StockInfo stockInfo = stockInfoCache.get(stockCategory.getStockCode());
        return stockInfo != null && stockInfo.isActiveAndOrdinary();
    }

    private List<StockSectorItem> toBlockingStockCategoryItems(
            List<CustomStockSector> stockCategories, Map<Long, CustomSector> categoryById) {
        Map<String, StockInfo> stockInfoCache = stockInfoCacheService.getCache();
        return stockCategories.stream()
                .map(stockCategory -> new StockSectorItem(
                        stockCategory.getStockCode(),
                        resolveStockName(stockCategory.getStockCode(), stockInfoCache),
                        categoryById.get(stockCategory.getSectorId()).getName()))
                .toList();
    }

    /** 캐시에 없는 종목이면 종목코드를 그대로 이름 자리에 넣는다 — findBlockingStockCategories가 활성
     * 주권만 통과시키므로 이 자리는 지금 도달 불가능하지만, "캐시가 낡았을 때"의 대가(5-3)를 실제 예외로
     * 만들지 않기 위한 방어다. */
    private String resolveStockName(String stockCode, Map<String, StockInfo> stockInfoCache) {
        StockInfo stockInfo = stockInfoCache.get(stockCode);
        return stockInfo != null ? stockInfo.getStockName() : stockCode;
    }

    private List<Long> collectSubCategoryIds(Long categoryId, Map<Long, List<CustomSector>> categoryByParentId) {
        List<Long> collectedIds = new ArrayList<>();
        collectedIds.add(categoryId);
        collectSubCategories(categoryId, categoryByParentId, collectedIds);
        return collectedIds;
    }

    private void collectSubCategories(
            Long parentId, Map<Long, List<CustomSector>> categoryByParentId, List<Long> collectedIds) {
        for (CustomSector child : categoryByParentId.getOrDefault(parentId, List.of())) {
            collectedIds.add(child.getId());
            collectSubCategories(child.getId(), categoryByParentId, collectedIds);
        }
    }

    private SectorItem toItem(CustomSector category) {
        return new SectorItem(category.getId(), category.getName(), category.getParentId(), category.getDepth());
    }
}
