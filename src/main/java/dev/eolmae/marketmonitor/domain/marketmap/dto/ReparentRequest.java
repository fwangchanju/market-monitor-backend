package dev.eolmae.marketmonitor.domain.marketmap.dto;

/** categoryId가 null이면 최상위(루트)로 이동하라는 의미. */
public record ReparentRequest(Long categoryId) {}
