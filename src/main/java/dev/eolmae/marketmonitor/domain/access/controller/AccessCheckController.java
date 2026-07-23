package dev.eolmae.marketmonitor.domain.access.controller;

import dev.eolmae.marketmonitor.domain.access.service.AllowedIpAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** nginx auth_request 전용 내부 엔드포인트. 외부에는 절대 노출되지 않고 nginx 서브리퀘스트로만 호출됨. */
@RestController
@RequiredArgsConstructor
public class AccessCheckController {

    private final AllowedIpAccessService allowedIpAccessService;

    @GetMapping("/internal/access-check/general")
    public void check(@RequestHeader("X-Real-IP") String ip) {
        if (!allowedIpAccessService.isAllowed(ip)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/internal/access-check/admin")
    public void checkAdmin(@RequestHeader("X-Real-IP") String ip) {
        if (!allowedIpAccessService.isAllowedAdmin(ip)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
