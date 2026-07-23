package dev.eolmae.marketmonitor.domain.access.repository;

import dev.eolmae.marketmonitor.domain.access.entity.AllowedIp;
import dev.eolmae.marketmonitor.domain.access.enums.Role;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedIpRepository extends JpaRepository<AllowedIp, String> {

    List<AllowedIp> findByRole(Role role);

    boolean existsByIpAndRole(String ip, Role role);

    void deleteByIpAndRole(String ip, Role role);
}
