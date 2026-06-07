package dev.eolmae.marketmonitor.domain.user.repository;

import dev.eolmae.marketmonitor.domain.user.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, Long> {

    Optional<UserNotificationSetting> findByUserUserKey(String userKey);

    List<UserNotificationSetting> findByReminderEnabledTrue();
}
