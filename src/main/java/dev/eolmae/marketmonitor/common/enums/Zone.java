package dev.eolmae.marketmonitor.common.enums;

import java.time.ZoneId;

public enum Zone {
    KST("Asia/Seoul");

    private final String id;

    Zone(String id) {
        this.id = id;
    }

    public ZoneId zoneId() {
        return ZoneId.of(id);
    }
}
