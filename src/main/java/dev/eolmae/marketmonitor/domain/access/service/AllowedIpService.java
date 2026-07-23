package dev.eolmae.marketmonitor.domain.access.service;

import dev.eolmae.marketmonitor.domain.access.dto.AllowedIpItem;
import dev.eolmae.marketmonitor.domain.access.entity.AllowedIp;
import dev.eolmae.marketmonitor.domain.access.enums.Role;
import dev.eolmae.marketmonitor.domain.access.repository.AllowedIpRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일반(USER) 허용 IP 관리. admin(ADMIN role) IP는 이 서비스로 관리하지 않고 DB에 직접 등록한다. */
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
}
