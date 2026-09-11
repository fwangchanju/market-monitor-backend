package dev.eolmae.marketmonitor.domain.notification.enums;

// 이번 tick에 무엇을 보낼지 — 안 보낼지(NONE), 섹터만(SECTOR_ONLY), 맵을 포함해서(WITH_MAP).
public enum TelegramSendKind {
    NONE,
    SECTOR_ONLY,
    WITH_MAP
}
