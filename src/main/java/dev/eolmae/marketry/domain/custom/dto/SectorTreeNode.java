package dev.eolmae.marketry.domain.custom.dto;

import java.util.List;

public record SectorTreeNode(String sectorName, List<SectorTreeNode> children, List<String> stockCodes) {}
