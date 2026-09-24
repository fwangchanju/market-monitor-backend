package dev.eolmae.marketmonitor.domain.custom.dto;

import jakarta.validation.constraints.NotNull;

public record SectorIdRequest(
        @NotNull(message = "카테고리를 선택해주세요.") Long categoryId) {}
