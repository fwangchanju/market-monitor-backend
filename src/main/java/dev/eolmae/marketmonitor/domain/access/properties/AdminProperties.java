package dev.eolmae.marketmonitor.domain.access.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "access.admin")
public record AdminProperties(List<String> tokens) {}
