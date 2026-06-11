package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.common.event.EscalationEvent;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 개발자(DEVELOPER_CHAT_ID)에게 에스컬레이션 알림을 발송하는 리스너. {@link EscalationEvent} 수신 → 텔레그램. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscalationNotifier {

    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    @EventListener
    public void onEscalation(EscalationEvent event) {
        String chatId = properties.developerChatId();
        if (StringUtils.isBlank(chatId)) {
            return;
        }
        telegramClient.sendMessage(chatId, event.message());
    }
}
