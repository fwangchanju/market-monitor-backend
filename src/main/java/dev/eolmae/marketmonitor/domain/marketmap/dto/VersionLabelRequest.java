package dev.eolmae.marketmonitor.domain.marketmap.dto;

import jakarta.validation.constraints.NotBlank;

public record VersionLabelRequest(
        @NotBlank(message = "버전명을 입력해주세요.") String label) {}
