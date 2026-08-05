package dev.eolmae.marketmonitor.domain.marketmap.dto;

import java.util.List;

public record CategoryTreeNode(
        String categoryName, int displayOrder, boolean isLocked, List<CategoryTreeNode> children, List<String> stockCodes) {}
