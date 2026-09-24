package dev.eolmae.marketmonitor.domain.custom.dto;

public record CustomValueTierItem(Long id, String label, Long thresholdValue, boolean isExcludedByDefault) {}
