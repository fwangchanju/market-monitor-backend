package dev.eolmae.marketmonitor.domain.stock.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kiwoom")
public record KiwoomProperties(String appKey, String secret) {}
