package dev.eolmae.marketmonitor.domain.marketmap.dto;

import dev.eolmae.marketmonitor.domain.marketmap.enums.ColorLabel;
import java.math.BigDecimal;

public record ScaleThresholdItem(Long id, BigDecimal thresholdPercent, String color, ColorLabel colorLabel) {}
