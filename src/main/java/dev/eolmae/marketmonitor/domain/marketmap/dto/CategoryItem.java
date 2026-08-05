package dev.eolmae.marketmonitor.domain.marketmap.dto;

public record CategoryItem(Long id, String name, Long parentId, int depth, int displayOrder, boolean isLocked) {}
