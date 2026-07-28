package dev.eolmae.marketmonitor.domain.notification.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-monitor")
public record MarketMonitorProperties(String baseUrl) {}
