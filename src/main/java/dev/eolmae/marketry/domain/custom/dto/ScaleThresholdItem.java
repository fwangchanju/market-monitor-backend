package dev.eolmae.marketry.domain.custom.dto;

import dev.eolmae.marketry.domain.custom.enums.ColorLabel;
import java.math.BigDecimal;

public record ScaleThresholdItem(Long id, BigDecimal thresholdPercent, String color, ColorLabel colorLabel) {}
