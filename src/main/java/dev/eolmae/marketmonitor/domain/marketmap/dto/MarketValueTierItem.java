package dev.eolmae.marketmonitor.domain.marketmap.dto;

public record MarketValueTierItem(Long id, String label, Long thresholdValue, boolean isExcludedByDefault) {}
