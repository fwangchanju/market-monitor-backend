package dev.eolmae.marketmonitor.domain.view.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SnapshotResponse<T>(LocalDateTime snapshotTime, List<T> items) {}
