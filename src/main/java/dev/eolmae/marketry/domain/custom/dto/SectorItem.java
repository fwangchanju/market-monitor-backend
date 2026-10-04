package dev.eolmae.marketry.domain.custom.dto;

public record SectorItem(Long id, String name, Long parentId, int depth) {}
