package dev.eolmae.marketmonitor.common.service;

import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.external.telegram.TelegramClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 개발자(DEVELOPER_CHAT_ID)에게 에스컬레이션 알림을 발송하는 서비스.
 * GlobalExceptionHandler(HTTP 컨텍스트)와 각 수집기 스케줄러(배치 컨텍스트) 양쪽에서 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    public void sendEscalation(EscalateException e) {
        String chatId = properties.developerChatId();
        if (chatId == null || chatId.isBlank()) {
            log.warn("DEVELOPER_CHAT_ID 미설정 — 에스컬레이션 알림 스킵: {}", e.getMessage());
            return;
        }

        String message = String.format(
                "⚠️ <b>에스컬레이션 오류 발생</b>\n\n<code>%s</code>\n\n%s", e.getClass().getSimpleName(), e.getMessage());

        telegramClient.sendMessage(chatId, message);
    }
}
