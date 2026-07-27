package dev.eolmae.marketmonitor.domain.access.service;

import dev.eolmae.marketmonitor.domain.access.entity.AdminToken;
import dev.eolmae.marketmonitor.domain.access.repository.AdminTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminTokenService {

    private final AdminTokenRepository adminTokenRepository;
    private final AllowedIpService allowedIpService;

    public void registerIp(String token, String ip) {
        AdminToken adminToken =
                adminTokenRepository.findById(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        if (ip.equals(adminToken.getCurrentIp())) {
            return;
        }

        // 필요한 경우 로그인 기능 추가
        allowedIpService.reregisterAdmin(adminToken.getCurrentIp(), ip);
        adminToken.updateCurrentIp(ip);
    }
}
