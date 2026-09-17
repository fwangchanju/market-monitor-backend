package dev.eolmae.marketmonitor.domain.notification.properties;

import dev.eolmae.marketmonitor.domain.view.enums.AverageMode;
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
        AverageMode averageMode,
        boolean sectorFilter,
        List<LocalTime> mapSendTimes) {}
