package dev.eolmae.marketmonitor.domain.marketmap.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(@NotBlank(message = "카테고리명을 입력해주세요.") String name, Long parentId) {}
