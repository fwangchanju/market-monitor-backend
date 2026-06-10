package dev.eolmae.marketmonitor.domain.dashboard.dto;

import java.time.LocalTime;

public record NotificationSettingResponse(
        String userKey, boolean reminderEnabled, LocalTime reminderTime, String timezone) {}
