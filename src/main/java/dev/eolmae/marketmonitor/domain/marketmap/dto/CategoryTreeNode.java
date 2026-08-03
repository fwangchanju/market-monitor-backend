package dev.eolmae.marketmonitor.domain.marketmap.dto;

import java.util.List;

public record CategoryTreeNode(
        String categoryName, int displayOrder, boolean isSynced, List<CategoryTreeNode> children, List<String> stockCodes) {}
