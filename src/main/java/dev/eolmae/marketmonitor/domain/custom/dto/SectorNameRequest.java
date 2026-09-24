package dev.eolmae.marketmonitor.domain.custom.dto;

import jakarta.validation.constraints.NotBlank;

public record SectorNameRequest(
        @NotBlank(message = "카테고리명을 입력해주세요.") String name) {}
