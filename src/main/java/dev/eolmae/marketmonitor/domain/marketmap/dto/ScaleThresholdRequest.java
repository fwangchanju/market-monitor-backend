package dev.eolmae.marketmonitor.domain.marketmap.dto;

import dev.eolmae.marketmonitor.domain.marketmap.enums.ColorLabel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record ScaleThresholdRequest(
        @NotNull(message = "기준 등락률을 입력해주세요.")
                @DecimalMin(value = "-30", message = "기준 등락률은 -30 이상이어야 합니다.")
                @DecimalMax(value = "30", message = "기준 등락률은 30 이하여야 합니다.")
                BigDecimal thresholdPercent,
        @NotNull(message = "색상을 지정해주세요.")
                @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "올바른 색상 값이 아닙니다.")
                String color,
        ColorLabel colorLabel) {}
