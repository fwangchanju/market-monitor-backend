package dev.eolmae.marketmonitor.domain.marketmap.dto;

import jakarta.validation.constraints.NotNull;

public record CategoryIdRequest(
        @NotNull(message = "카테고리를 선택해주세요.") Long categoryId) {}
