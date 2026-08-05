package dev.eolmae.marketmonitor.domain.access.service;

import dev.eolmae.marketmonitor.domain.access.entity.AdminToken;
import dev.eolmae.marketmonitor.domain.access.repository.AdminTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminTokenService {

    private final AdminTokenRepository adminTokenRepository;
    private final AllowedIpService allowedIpService;
    private final AllowedIpAccessService allowedIpAccessService;

    public void registerIp(String token, String currentIp) {
        AdminToken adminToken =
                adminTokenRepository.findById(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        String lastIp = adminToken.getLastIp();
        if (currentIp.equals(lastIp) && allowedIpAccessService.isAllowedAdmin(currentIp)) {
            return;
        }

        // 필요한 경우 로그인 기능 추가
        allowedIpService.reregisterAdmin(lastIp, currentIp);
        adminToken.updateLastIp(currentIp);
    }
}
