package dev.eolmae.marketmonitor.domain.notification.properties;

import dev.eolmae.marketmonitor.domain.notification.enums.TelegramOverlap;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        String chatId,
        String developerChatId,
        int sendMinute,
        List<Integer> sendIntervalMinutes,
        TelegramOverlap overlap) {}
