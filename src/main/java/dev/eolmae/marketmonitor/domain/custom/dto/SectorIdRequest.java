package dev.eolmae.marketmonitor.domain.custom.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public record SectorIdRequest(
        @NotNull(message = "섹터를 선택해주세요.") @JsonAlias("categoryId")
        Long sectorId) {}
