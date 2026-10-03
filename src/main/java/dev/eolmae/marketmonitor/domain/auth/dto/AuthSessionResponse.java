package dev.eolmae.marketmonitor.domain.auth.dto;

import dev.eolmae.marketmonitor.domain.auth.enums.Role;

public record AuthSessionResponse(
        boolean authenticated, Long userId, String email, Role role, String nickname, Long profileImageVersion) {

    public static AuthSessionResponse anonymous() {
        return new AuthSessionResponse(false, null, null, null, null, null);
    }
}
