package dev.eolmae.marketmonitor.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import dev.eolmae.marketmonitor.common.event.EscalationEvent;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.client.TelegramClient;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EscalationNotifierTest {

    private final TelegramClient telegramClient = Mockito.mock(TelegramClient.class);
    private final TelegramProperties properties = new TelegramProperties("token", "chat", "dev-chat", 10);
    private final EscalationNotifier notifier = new EscalationNotifier(telegramClient, properties);

    @Test
    void onEscalation_발송이_실패해도_예외를_전파하지_않는다() {
        doThrow(new EscalateException(ErrorCode.TELEGRAM_MESSAGE_SEND_FAILED))
                .when(telegramClient)
                .sendMessage("dev-chat", "장애 발생");

        assertThatCode(() -> notifier.onEscalation(new EscalationEvent("장애 발생")))
                .doesNotThrowAnyException();
    }

    @Test
    void onEscalation_개발자_채팅방_아이디가_없으면_발송하지_않는다() {
        TelegramProperties noDevChat = new TelegramProperties("token", "chat", "", 10);
        EscalationNotifier withoutDevChat = new EscalationNotifier(telegramClient, noDevChat);

        withoutDevChat.onEscalation(new EscalationEvent("장애 발생"));

        Mockito.verifyNoInteractions(telegramClient);
    }

    @Test
    void onEscalation_정상_발송되면_그대로_전달한다() {
        notifier.onEscalation(new EscalationEvent("장애 발생"));

        verify(telegramClient).sendMessage("dev-chat", "장애 발생");
    }
}
