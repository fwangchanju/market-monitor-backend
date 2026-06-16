package dev.eolmae.marketmonitor.domain.renderer.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "renderer")
public record RendererProperties(String url) {}
