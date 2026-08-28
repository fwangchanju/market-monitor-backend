package dev.eolmae.marketmonitor.domain.view.dto;

import java.math.BigDecimal;

public record SnapshotAverages(BigDecimal weightedAvgChangeRate, BigDecimal simpleAvgChangeRate) {}
