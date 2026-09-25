package dev.eolmae.marketmonitor.domain.auth.repository;

import dev.eolmae.marketmonitor.domain.auth.entity.UserRefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserRefreshToken> findByTokenHash(String tokenHash);
}
