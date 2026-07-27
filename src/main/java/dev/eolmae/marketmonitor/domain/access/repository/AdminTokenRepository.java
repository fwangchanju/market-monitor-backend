package dev.eolmae.marketmonitor.domain.access.repository;

import dev.eolmae.marketmonitor.domain.access.entity.AdminToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminTokenRepository extends JpaRepository<AdminToken, String> {}
