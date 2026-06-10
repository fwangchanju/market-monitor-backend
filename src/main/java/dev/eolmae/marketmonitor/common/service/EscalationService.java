package dev.eolmae.marketmonitor.common.service;

import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import dev.eolmae.marketmonitor.external.telegram.TelegramClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 개발자(DEVELOPER_CHAT_ID)에게 에스컬레이션 알림을 발송하는 서비스.
 * GlobalExceptionHandler(HTTP 컨텍스트)와 각 수집기 스케줄러(배치 컨텍스트) 양쪽에서 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    public void notificationError(EscalateException e) {
        String chatId = properties.developerChatId();

        if (StringUtils.isBlank(chatId)) {
            return;
        }

        telegramClient.sendMessage(chatId, e.createMessage());
    }
}
