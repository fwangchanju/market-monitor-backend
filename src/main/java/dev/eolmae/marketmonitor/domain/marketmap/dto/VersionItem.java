package dev.eolmae.marketmonitor.domain.marketmap.dto;

import java.time.LocalDateTime;

public record VersionItem(Long id, String label, LocalDateTime createdAt, LocalDateTime updatedAt) {}
