package dev.eolmae.marketmonitor.domain.marketmap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryTreeNode;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 라이브 카테고리 트리 조립 및 버전 스냅샷 직렬화/복원. */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketMapCategoryTreeService {

    /** Collectors.groupingBy는 classifier가 null을 반환하면 예외를 던지므로, 루트(parent_id null)를 묶기 위한 대체 키. */
    private static final long ROOT_KEY = 0L;

    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final ObjectMapper objectMapper;

    List<CategoryTreeNode> buildTree() {
        List<MarketMapCategory> categories = marketMapCategoryRepository.findAll();
        List<MarketMapStockCategory> stockCategories = marketMapStockCategoryRepository.findAll();
        return assemble(categories, stockCategories);
    }

    private List<CategoryTreeNode> assemble(
            List<MarketMapCategory> categories, List<MarketMapStockCategory> stockCategories) {
        Map<Long, List<MarketMapCategory>> childrenByParentId = categories.stream()
                .collect(Collectors.groupingBy(category -> category.getParentId() == null ? ROOT_KEY : category.getParentId()));
        Map<Long, List<MarketMapStockCategory>> stocksByCategoryId =
                stockCategories.stream().collect(Collectors.groupingBy(MarketMapStockCategory::getCategoryId));

        return childrenByParentId.getOrDefault(ROOT_KEY, List.of()).stream()
                .sorted(Comparator.comparingInt(MarketMapCategory::getDisplayOrder))
                .map(category -> toNode(category, childrenByParentId, stocksByCategoryId))
                .toList();
    }

    private CategoryTreeNode toNode(
            MarketMapCategory category,
            Map<Long, List<MarketMapCategory>> childrenByParentId,
            Map<Long, List<MarketMapStockCategory>> stocksByCategoryId) {
        List<CategoryTreeNode> children = childrenByParentId.getOrDefault(category.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(MarketMapCategory::getDisplayOrder))
                .map(child -> toNode(child, childrenByParentId, stocksByCategoryId))
                .toList();
        List<String> stockCodes = stocksByCategoryId.getOrDefault(category.getId(), List.of()).stream()
                .map(MarketMapStockCategory::getStockCode)
                .toList();
        return new CategoryTreeNode(category.getName(), category.getDisplayOrder(), children, stockCodes);
    }

    @Transactional(readOnly = true)
    public String serializeCurrentSnapshot() {
        return toJson(buildTree());
    }

    public String toJson(List<CategoryTreeNode> tree) {
        try {
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.CATEGORY_TREE_SERIALIZE_FAILED, e);
        }
    }

    public List<CategoryTreeNode> parseJson(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<List<CategoryTreeNode>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.CATEGORY_TREE_PARSE_FAILED, e);
        }
    }

    /** 라이브 테이블을 스냅샷 내용으로 완전히 교체하고, 새로 생성되는 카테고리 전체를 주어진 버전으로 태깅한다. */
    public void restore(List<CategoryTreeNode> tree, Long versionId) {
        marketMapStockCategoryRepository.deleteAllInBatch();
        marketMapCategoryRepository.deleteAllInBatch();

        for (CategoryTreeNode node : tree) {
            insertNode(node, null, versionId);
        }
    }

    private void insertNode(CategoryTreeNode node, MarketMapCategory parent, Long versionId) {
        MarketMapCategory category = parent == null
                ? MarketMapCategory.createParent(node.categoryName(), node.displayOrder())
                : MarketMapCategory.createChild(node.categoryName(), parent, node.displayOrder());
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
