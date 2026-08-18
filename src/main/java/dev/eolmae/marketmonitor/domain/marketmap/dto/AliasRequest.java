package dev.eolmae.marketmonitor.domain.marketmap.dto;

import jakarta.validation.constraints.Size;

public record AliasRequest(@Size(max = 50, message = "약칭은 50자 이하로 입력해주세요.") String alias) {}
