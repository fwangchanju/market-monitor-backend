package dev.eolmae.marketmonitor.api.dto;

import java.time.LocalTime;

public record NotificationSettingResponse(
        String userKey, boolean reminderEnabled, LocalTime reminderTime, String timezone) {}
