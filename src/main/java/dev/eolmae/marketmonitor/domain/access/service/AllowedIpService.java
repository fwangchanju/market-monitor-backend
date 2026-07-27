package dev.eolmae.marketmonitor.domain.access.service;

import dev.eolmae.marketmonitor.domain.access.dto.AllowedIpItem;
import dev.eolmae.marketmonitor.domain.access.entity.AllowedIp;
import dev.eolmae.marketmonitor.domain.access.enums.Role;
import dev.eolmae.marketmonitor.domain.access.repository.AllowedIpRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** IP 화이트리스트 관리. */
@Service
@RequiredArgsConstructor
@Transactional
public class AllowedIpService {

    private final AllowedIpRepository allowedIpRepository;
    private final AllowedIpAccessService allowedIpAccessService;

    @Transactional(readOnly = true)
    public List<AllowedIpItem> list() {
        return allowedIpRepository.findByRole(Role.USER).stream()
                .map(ip -> new AllowedIpItem(ip.getIp(), ip.getCreatedAt()))
                .toList();
    }

    public void register(String ip) {
        allowedIpRepository
                .findById(ip)
                .ifPresentOrElse(existing -> {}, () -> allowedIpRepository.save(AllowedIp.create(ip, Role.USER)));
        allowedIpAccessService.invalidate(ip);
    }

    public void delete(String ip) {
        allowedIpRepository.deleteByIpAndRole(ip, Role.USER);
        allowedIpAccessService.invalidate(ip);
    }

    public void reregisterAdmin(String oldIp, String newIp) {
        if (oldIp != null) {
            allowedIpRepository.deleteByIpAndRole(oldIp, Role.ADMIN);
        }
        allowedIpRepository
                .findById(newIp)
                .ifPresentOrElse(existing -> {}, () -> allowedIpRepository.save(AllowedIp.create(newIp, Role.ADMIN)));
    }
}
