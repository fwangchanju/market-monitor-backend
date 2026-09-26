package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {}
