package dev.eolmae.marketmonitor.domain.user.repository;

import dev.eolmae.marketmonitor.domain.user.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUserKey(String userKey);

    Optional<AppUser> findByTelegramChatId(String telegramChatId);
}
