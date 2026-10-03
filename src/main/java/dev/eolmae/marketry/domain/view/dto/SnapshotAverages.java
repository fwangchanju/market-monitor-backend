package dev.eolmae.marketry.domain.view.dto;

import java.math.BigDecimal;

public record SnapshotAverages(BigDecimal weightedAvgChangeRate, BigDecimal simpleAvgChangeRate) {}
