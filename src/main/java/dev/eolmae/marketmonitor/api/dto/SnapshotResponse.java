package dev.eolmae.marketmonitor.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SnapshotResponse<T>(LocalDateTime snapshotTime, List<T> items) {}
