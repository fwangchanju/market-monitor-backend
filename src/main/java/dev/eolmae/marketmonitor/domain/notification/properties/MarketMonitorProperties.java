package dev.eolmae.marketmonitor.domain.notification.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-monitor")
public record MarketMonitorProperties(String baseUrl, Long ownerUserId) {

    /** 로그인 사용자가 없을 때(스케줄러 등) 커스텀 데이터 소유자로 폴백한다 — currentUserId가 null이면
     * ownerUserId를 쓴다. */
    public Long userIdOrOwner(Long currentUserId) {
        return currentUserId == null ? ownerUserId : currentUserId;
    }
}
