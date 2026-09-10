package dev.eolmae.marketmonitor.domain.notification.enums;

// 두 발송 주기가 같은 tick에 겹칠 때: 전부 보낼지(ALL), 가장 긴 주기만 보낼지(LONGEST_ONLY).
public enum TelegramOverlap {
    ALL,
    LONGEST_ONLY
}
