package dev.eolmae.marketmonitor.domain.marketmap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.common.exception.BadRequestException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryTreeNode;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import dev.eolmae.marketmonitor.domain.stock.entity.StockInfo;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 라이브 카테고리 트리 조립 및 버전 스냅샷 직렬화/복원. */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketMapCategoryTreeService {

    /** Collectors.groupingBy는 classifier가 null을 반환하면 예외를 던지므로, parent_id가 null인 카테고리를 묶기 위한 대체 키. */
    private static final long NO_PARENT_KEY = 0L;

    private static final String UNCATEGORIZED = "미분류";

    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final MarketMapCategoryService marketMapCategoryService;
    private final StockInfoCacheService stockInfoCacheService;
    private final ObjectMapper objectMapper;

    List<CategoryTreeNode> buildTree() {
        List<MarketMapCategory> categories = marketMapCategoryRepository.findAll();
        List<MarketMapStockCategory> stockCategories = marketMapStockCategoryRepository.findAll();
        return assemble(categories, stockCategories);
    }

    private List<CategoryTreeNode> assemble(
            List<MarketMapCategory> categories, List<MarketMapStockCategory> stockCategories) {
        Map<Long, List<MarketMapCategory>> childrenByParentId = categories.stream()
                .collect(Collectors.groupingBy(
                        category -> category.hasNoParent() ? NO_PARENT_KEY : category.getParentId()));
        Map<Long, List<MarketMapStockCategory>> stocksByCategoryId =
                stockCategories.stream().collect(Collectors.groupingBy(MarketMapStockCategory::getCategoryId));

        return childrenByParentId.getOrDefault(NO_PARENT_KEY, List.of()).stream()
                .map(category -> toNode(category, childrenByParentId, stocksByCategoryId))
                .toList();
    }

    private CategoryTreeNode toNode(
            MarketMapCategory category,
            Map<Long, List<MarketMapCategory>> childrenByParentId,
            Map<Long, List<MarketMapStockCategory>> stocksByCategoryId) {
        List<CategoryTreeNode> children = childrenByParentId.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> toNode(child, childrenByParentId, stocksByCategoryId))
                .toList();
        List<String> stockCodes = stocksByCategoryId.getOrDefault(category.getId(), List.of()).stream()
                .map(MarketMapStockCategory::getStockCode)
                .toList();
        return new CategoryTreeNode(category.getName(), children, stockCodes);
    }

    @Transactional(readOnly = true)
    public String serializeCurrentSnapshot() {
        return toJson(buildTree());
    }

    public String toJson(List<CategoryTreeNode> tree) {
        try {
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.CATEGORY_TREE_SERIALIZE_FAILED, e);
        }
    }

    public List<CategoryTreeNode> parseJson(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<List<CategoryTreeNode>>() {});
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.CATEGORY_TREE_PARSE_FAILED, e);
        }
    }

    /** 라이브 테이블을 스냅샷 내용으로 완전히 교체하고, 새로 생성되는 카테고리 전체를 주어진 버전으로 태깅한다.
     * 스냅샷은 저장 시점 기준이라, 그 이후 신규 상장된 종목(및 그 종목만 쓰는 신규 카테고리)은 스냅샷에 없어서
     * 복원 직후엔 배정이 빠진 채로 남는다. stock_info 기준 활성 주권 종목과 방금 복원된 배정을 비교해 누락분을
     * 채워, "활성 주권 종목은 항상 market_map_stock_category에 배정돼 있다"는 불변식을 복원 후에도 유지한다. */
    public void restore(List<CategoryTreeNode> tree, Long versionId) {
        marketMapStockCategoryRepository.deleteAllInBatch();
        marketMapCategoryRepository.deleteAllInBatch();

        for (CategoryTreeNode node : tree) {
            insertNode(node, null, versionId);
        }

        marketMapCategoryService.restoreMissingStockCategories(findStocksMissingAfterRestore());
    }

    private List<StockInfoSyncedEvent.NewStock> findStocksMissingAfterRestore() {
        Set<String> restoredStockCodes = marketMapStockCategoryRepository.findAll().stream()
                .map(MarketMapStockCategory::getStockCode)
                .collect(Collectors.toSet());

        return stockInfoCacheService.getCache().values().stream()
                .filter(StockInfo::isActiveAndOrdinary)
                .filter(stockInfo -> !restoredStockCodes.contains(stockInfo.getStockCode()))
                .map(stockInfo -> new StockInfoSyncedEvent.NewStock(
                        stockInfo.getStockCode(), normalizeCategoryName(stockInfo.getCategoryName())))
                .toList();
    }

    private String normalizeCategoryName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return UNCATEGORIZED;
        }
        return categoryName;
    }

    private void insertNode(CategoryTreeNode node, MarketMapCategory parent, Long versionId) {
        MarketMapCategory category = parent == null
                ? MarketMapCategory.createParent(node.categoryName())
                : MarketMapCategory.createChild(node.categoryName(), parent);
        category.tagVersion(versionId);
        MarketMapCategory saved = marketMapCategoryRepository.save(category);

        for (String stockCode : node.stockCodes()) {
            marketMapStockCategoryRepository.save(MarketMapStockCategory.create(stockCode, saved.getId()));
        }

        for (CategoryTreeNode child : node.children()) {
            insertNode(child, saved, versionId);
        }
    }
}
