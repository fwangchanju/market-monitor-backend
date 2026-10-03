package dev.eolmae.marketry.domain.custom.repository;

import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomValueTierThresholdRepository extends JpaRepository<CustomValueTierThreshold, Long> {

    List<CustomValueTierThreshold> findAllByUserIdOrderByThresholdValueAsc(Long userId);

    void deleteAllByUserId(Long userId);
}
