package dev.eolmae.marketmonitor.domain.custom.dto;

import dev.eolmae.marketmonitor.domain.custom.enums.ColorLabel;
import java.math.BigDecimal;

public record ScaleThresholdItem(Long id, BigDecimal thresholdPercent, String color, ColorLabel colorLabel) {}
