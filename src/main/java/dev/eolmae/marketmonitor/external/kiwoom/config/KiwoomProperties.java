package dev.eolmae.marketmonitor.external.kiwoom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kiwoom")
public record KiwoomProperties(String appKey, String secret) {}
