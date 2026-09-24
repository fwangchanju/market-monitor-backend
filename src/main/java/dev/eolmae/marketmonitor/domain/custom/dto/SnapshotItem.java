package dev.eolmae.marketmonitor.domain.custom.dto;

import java.time.LocalDateTime;

public record SnapshotItem(Long id, String label, LocalDateTime createdAt, LocalDateTime updatedAt) {}
