package dev.eolmae.marketmonitor.domain.notification.service;

import dev.eolmae.marketmonitor.domain.user.AppUser;
import dev.eolmae.marketmonitor.domain.user.UserNotificationSetting;
import dev.eolmae.marketmonitor.external.telegram.TelegramClient;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramNotifier {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TelegramClient telegramClient;

    @Value("${dashboard.base-url}")
    private String dashboardBaseUrl;

    public void sendReminder(UserNotificationSetting setting, LocalTime reminderTime) {
        AppUser user = setting.getUser();
        String chatId = user.getTelegramChatId();

        if (chatId == null || chatId.isBlank()) {
            log.warn("텔레그램 chatId 없음, 발송 스킵: userKey={}", user.getUserKey());
            return;
        }

        String timeLabel = reminderTime.format(TIME_FORMATTER);
        String message = String.format("%s 체크 시간입니다.\n\n<a href=\"%s\">대시보드 열기</a>", timeLabel, dashboardBaseUrl);

        telegramClient.sendMessage(chatId, message);
        log.info("리마인더 발송 완료: userKey={}, reminderTime={}", user.getUserKey(), reminderTime);
    }
}
