package dev.eolmae.marketmonitor.domain.auth.dto;

public record ProfileResponse(String nickname, boolean hasImage, Long imageVersion) {

    public static ProfileResponse empty() {
        return new ProfileResponse(null, false, null);
    }
}
