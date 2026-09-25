package dev.eolmae.marketmonitor.domain.auth.repository;

import dev.eolmae.marketmonitor.domain.auth.entity.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByIssuerAndSub(String issuer, String sub);
}
