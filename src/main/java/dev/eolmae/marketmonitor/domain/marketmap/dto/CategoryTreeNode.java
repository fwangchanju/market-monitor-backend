package dev.eolmae.marketmonitor.domain.marketmap.dto;

import java.util.List;

public record CategoryTreeNode(
        String categoryName, int displayOrder, List<CategoryTreeNode> children, List<String> stockCodes) {}
