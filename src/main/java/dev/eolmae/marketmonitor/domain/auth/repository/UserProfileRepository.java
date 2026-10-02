package dev.eolmae.marketmonitor.domain.auth.repository;

import dev.eolmae.marketmonitor.domain.auth.entity.UserProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    <T> Optional<T> findByUserId(Long userId, Class<T> type);

    boolean existsByNicknameIgnoreCaseAndUserIdNot(String nickname, Long userId);
}
