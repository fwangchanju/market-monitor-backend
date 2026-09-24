package dev.eolmae.marketmonitor.domain.custom.repository;

import dev.eolmae.marketmonitor.domain.custom.entity.CustomSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomSnapshotRepository extends JpaRepository<CustomSnapshot, Long> {

    List<CustomSnapshot> findAllByOrderByCreatedAtDesc();
}
