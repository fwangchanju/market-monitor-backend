package dev.eolmae.marketmonitor.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramMediaItem(
        @JsonProperty("type") String type,
        @JsonProperty("media") String media) {

    public static TelegramMediaItem photo(int index) {
        return new TelegramMediaItem("photo", "attach://photo" + index);
    }
}
