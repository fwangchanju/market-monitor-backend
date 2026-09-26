package dev.eolmae.marketmonitor.domain.custom.dto;

/** parentId가 null이면 최상위(루트)로 이동한다. */
public record ReparentRequest(Long parentId) {}
