package dev.eolmae.marketmonitor.domain.custom.dto;

import java.util.List;

public record SectorTreeNode(String sectorName, List<SectorTreeNode> children, List<String> stockCodes) {}
