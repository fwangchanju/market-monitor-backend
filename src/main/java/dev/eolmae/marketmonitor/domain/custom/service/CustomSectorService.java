package dev.eolmae.marketmonitor.domain.custom.service;

import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.custom.dto.SectorDeletePreview;
import dev.eolmae.marketmonitor.domain.custom.dto.SectorItem;
import dev.eolmae.marketmonitor.domain.custom.dto.StockSectorItem;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.repository.StockInfoRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 추가/삭제/재부모화. 항상 라이브(현재 표시 중인) 트리만을 대상으로 한다. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CustomSectorService {

    private static final String UNCATEGORIZED = "미분류";

    private final CustomSectorRepository customSectorRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final StockInfoRepository stockInfoRepository;
    private final StockInfoCacheService stockInfoCacheService;

    @Transactional(readOnly = true)
    public List<SectorItem> getCategories() {
        return findAllCategories().stream().map(this::toItem).toList();
    }

    /** stock -> marketmap 순환 의존을 피하려고 이벤트로 수신(StockInfoSyncedEvent 참고).
     * 이벤트 payload(신규 종목)에 더해, "활성 일반주인데 아직 custom_stock_sector에 배정 행이
     * 없는 종목"도 직접 계산해서 함께 채운다 — 이미 stock_info에 있던 종목이 나중에 일반주가 되는
     * 경우(ETF로 등록됐다가 marketCode가 바뀌는 등)는 이벤트에 실리지 않아 배정을 영영 못 받기
     * 때문이다. StockInfoCollector가 이벤트에 신규 종목만 싣는 것은 그대로 둔다. */
    @EventListener
    public void onStockInfoSynced(StockInfoSyncedEvent event) {
        List<StockInfoSyncedEvent.NewStock> stocks = new ArrayList<>(event.newStocks());
        stocks.addAll(findMissingAssignments(event.newStocks()));
        syncStockCategories(stocks);
    }

    /** stockInfoCacheService가 아니라 StockInfoRepository를 직접 조회한다 — evict가 커밋 후로
     * 밀리면(2-4) 이 시점의 캐시엔 방금 저장된 신규 종목이 아직 없을 수 있다.
     * 계산 방식은 CustomSectorTreeService.findStocksMissingAfterRestore()와 동일하다. */
    private List<StockInfoSyncedEvent.NewStock> findMissingAssignments(List<StockInfoSyncedEvent.NewStock> newStocks) {
        Set<String> alreadyHandled =
                newStocks.stream().map(StockInfoSyncedEvent.NewStock::stockCode).collect(Collectors.toSet());
        Set<String> assignedStockCodes = customStockSectorRepository.findAll().stream()
                .map(CustomStockSector::getStockCode)
                .collect(Collectors.toSet());

        return stockInfoRepository.findByActiveTrue().stream()
                .filter(StockInfo::isActiveAndOrdinary)
                .filter(stockInfo -> !alreadyHandled.contains(stockInfo.getStockCode()))
                .filter(stockInfo -> !assignedStockCodes.contains(stockInfo.getStockCode()))
                .map(stockInfo ->
                        new StockInfoSyncedEvent.NewStock(stockInfo.getStockCode(), normalizeCategoryName(stockInfo)))
                .toList();
    }

    private String normalizeCategoryName(StockInfo stockInfo) {
        String categoryName = stockInfo.getIndustryName();
        if (categoryName == null || categoryName.isBlank()) {
            return UNCATEGORIZED;
        }
        return categoryName;
    }

    /** 버전 복원(CustomSectorTreeService.restore) 후 스냅샷에 없던(=배정이 빠진) 종목을 채워넣는 진입점.
     * 실제 로직은 라이브 동기화(onStockInfoSynced)와 동일한 syncStockCategories를 그대로 재사용한다. */
    public void restoreMissingStockCategories(List<StockInfoSyncedEvent.NewStock> stocks) {
        syncStockCategories(stocks);
    }

    /** 주어진 종목 중 카테고리명이 아직 없는 것만 최상위 카테고리로 생성한 뒤 custom_stock_sector에 배정한다. */
    private void syncStockCategories(List<StockInfoSyncedEvent.NewStock> stocks) {
        if (stocks.isEmpty()) {
            return;
        }

        Set<String> categoryNames =
                stocks.stream().map(StockInfoSyncedEvent.NewStock::categoryName).collect(Collectors.toSet());
        Map<String, CustomSector> categoryByName = createMissingCategories(categoryNames);
        createNewStockCategories(stocks, categoryByName);
    }

    private void createNewStockCategories(
            List<StockInfoSyncedEvent.NewStock> newStocks, Map<String, CustomSector> categoryByName) {
        List<CustomStockSector> newStockCategories = newStocks.stream()
                .map(stock -> CustomStockSector.create(
                        stock.stockCode(),
                        categoryByName.get(stock.categoryName()).getId()))
                .toList();
        customStockSectorRepository.saveAll(newStockCategories);
    }

    /** 기존 + 신규 생성분을 합친 이름별 맵을 리턴해서, 호출부가 다시 전체 조회할 필요가 없게 한다. */
    private Map<String, CustomSector> createMissingCategories(Set<String> categoryNames) {
        Map<String, CustomSector> existingByName =
                findAllCategories().stream().collect(Collectors.toMap(CustomSector::getName, Function.identity()));

        categoryNames.stream()
                .filter(name -> !existingByName.containsKey(name))
                .map(CustomSector::createParent)
                .forEach(category -> {
                    customSectorRepository.save(category);
                    existingByName.put(category.getName(), category);
                });

        return existingByName;
    }

    public SectorItem createParent(String name) {
        if (customSectorRepository.existsByName(name)) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_DUPLICATE, name);
        }
        CustomSector category = CustomSector.createParent(name);
        return toItem(customSectorRepository.save(category));
    }

    public SectorItem createChild(String name, Long parentId) {
        if (customSectorRepository.existsByName(name)) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_DUPLICATE, name);
        }
        CustomSector parent = customSectorRepository
                .findById(parentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, parentId));
        CustomSector category = CustomSector.createChild(name, parent);
        return toItem(customSectorRepository.save(category));
    }

    public void exclude(Long categoryId) {
        CustomSector target = customSectorRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId));
        target.exclude();
    }

    public void include(Long categoryId) {
        CustomSector target = customSectorRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId));
        target.include();
    }

    public void resetExcludes() {
        findAllCategories().forEach(CustomSector::include);
    }

    public void rename(Long categoryId, String name) {
        CustomSector target = customSectorRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND, categoryId));
        if (target.getName().equals(name)) {
            return;
        }
        if (customSectorRepository.existsByName(name)) {
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
        CategoryMaps maps = getCategoryMaps();
        Map<Long, CustomSector> categoryById = maps.categoryById();
        CustomSector target = findCategory(categoryId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, maps.categoryByParentId());
        List<CustomStockSector> stockCategories = customStockSectorRepository.findBySectorIdIn(subCategoryIds);
        List<CustomStockSector> blockingStockCategories = findBlockingStockCategories(stockCategories);
        if (!blockingStockCategories.isEmpty()) {
            return SectorDeletePreview.blocked(
                    target.getName(), toBlockingStockCategoryItems(blockingStockCategories, categoryById));
        }
        return SectorDeletePreview.deletable(
                target.getName(), toDeletableCategoryNames(categoryId, subCategoryIds, categoryById));
    }

    public void delete(Long categoryId) {
        CategoryMaps maps = getCategoryMaps();
        Map<Long, CustomSector> categoryById = maps.categoryById();
        findCategory(categoryId, categoryById);

        List<Long> subCategoryIds = collectSubCategoryIds(categoryId, maps.categoryByParentId());
        List<CustomStockSector> stockCategories = customStockSectorRepository.findBySectorIdIn(subCategoryIds);
        if (!findBlockingStockCategories(stockCategories).isEmpty()) {
            throw new ConflictException(ErrorCode.CATEGORY_HAS_ASSIGNED_STOCK, categoryId);
        }

        // 활성 주권 배정은 없다고 확인했지만(위 판정), 비활성 종목의 배정 행은 여전히 남아있을 수 있다 —
        // custom_sector를 가리키는 FK라 카테고리 삭제 전에 먼저 지워야 한다(결정 1).
        if (!stockCategories.isEmpty()) {
            log.info("[카테고리삭제] 비활성 배정 행 삭제 | categoryId={}|count={}", categoryId, stockCategories.size());
            customStockSectorRepository.deleteBySectorIdIn(subCategoryIds);
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
        List<CustomSector> categories = findAllCategories();
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

    private List<CustomSector> findAllCategories() {
        return customSectorRepository.findAll();
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
