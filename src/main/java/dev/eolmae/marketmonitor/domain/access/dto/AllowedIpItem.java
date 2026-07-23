package dev.eolmae.marketmonitor.domain.access.dto;

import java.time.LocalDateTime;

public record AllowedIpItem(String ip, LocalDateTime createdAt) {}
