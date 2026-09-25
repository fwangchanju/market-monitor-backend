package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.domain.stock.entity.IndustryInfo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndustryInfoRepository extends JpaRepository<IndustryInfo, Long> {

    Optional<IndustryInfo> findByName(String name);

    List<IndustryInfo> findByNameIn(Collection<String> names);
}
