package dev.eolmae.marketmonitor.domain.notification.properties;

import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        String chatId,
        String developerChatId,
        int sendMinute,
        int sendIntervalMinutes,
        int beforeMinutes,
        List<LocalTime> mapSendTimes) {}
