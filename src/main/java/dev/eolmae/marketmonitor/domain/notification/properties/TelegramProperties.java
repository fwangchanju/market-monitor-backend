package dev.eolmae.marketmonitor.domain.notification.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(String botToken, String chatId, String developerChatId, int sendMinute) {}
