package dev.eolmae.marketmonitor.domain.custom.dto;

public record SectorItem(Long id, String name, Long parentId, int depth) {}
