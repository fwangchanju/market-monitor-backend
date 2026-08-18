package dev.eolmae.marketmonitor.domain.marketmap.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkAssignRequest(
        @NotEmpty(message = "종목을 하나 이상 선택해주세요.") List<String> stockCodes,
        @NotNull(message = "카테고리를 선택해주세요.") Long categoryId) {}
