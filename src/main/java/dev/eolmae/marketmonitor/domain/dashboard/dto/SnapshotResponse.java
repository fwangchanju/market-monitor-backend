package dev.eolmae.marketmonitor.domain.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SnapshotResponse<T>(LocalDateTime snapshotTime, List<T> items) {}
