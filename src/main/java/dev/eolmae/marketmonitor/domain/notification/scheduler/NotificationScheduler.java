package dev.eolmae.marketmonitor.domain.notification.scheduler;

import dev.eolmae.marketmonitor.domain.notification.service.TelegramNotifier;
import dev.eolmae.marketmonitor.domain.user.UserNotificationSetting;
import dev.eolmae.marketmonitor.domain.user.repository.UserNotificationSettingRepository;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final UserNotificationSettingRepository settingRepository;
    private final TelegramNotifier telegramNotifier;

    /**
     * 매분 실행: 알림 활성화된 사용자의 설정 시각과 현재 시각 비교 후 발송
     */
    // @Scheduled(cron = "0 * * * * *")
    public void checkAndNotify() {
        List<UserNotificationSetting> settings = settingRepository.findByReminderEnabledTrue();
        if (settings.isEmpty()) {
            return;
        }

        for (UserNotificationSetting setting : settings) {
            try {
                checkAndSend(setting);
            } catch (Exception e) {
                log.error("리마인더 처리 실패: settingId={}", setting.getId(), e);
            }
        }
    }

    private void checkAndSend(UserNotificationSetting setting) {
        LocalTime now =
                LocalTime.now(ZoneId.of(setting.getTimezone())).withSecond(0).withNano(0);

        LocalTime reminderTime = setting.getReminderTime().withSecond(0).withNano(0);

        if (now.equals(reminderTime)) {
            telegramNotifier.sendReminder(setting, reminderTime);
        }
    }
}
