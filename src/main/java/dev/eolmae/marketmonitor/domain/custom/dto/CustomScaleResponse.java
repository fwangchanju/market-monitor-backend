package dev.eolmae.marketmonitor.domain.custom.dto;

import java.util.List;

public record CustomScaleResponse(List<ScaleThresholdItem> thresholds) {}
