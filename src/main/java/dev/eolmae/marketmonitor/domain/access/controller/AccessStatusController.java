package dev.eolmae.marketmonitor.domain.access.controller;

import dev.eolmae.marketmonitor.domain.access.dto.AdminStatusResponse;
import dev.eolmae.marketmonitor.domain.access.service.AllowedIpAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 프론트가 "지금 이 IP가 admin인지"를 확인하는 공개 엔드포인트. /api/ 하위(일반 화이트리스트 검사만 통과하면
 * 호출 가능)라 admin이 아닌 IP도 응답을 받을 수 있어야 한다 — admin 전용 UI(커스텀 버튼 등) 노출 여부를
 * 프론트가 스스로 판단하는 데 쓰인다. */
@RequestMapping("/api/access")
@RestController
@RequiredArgsConstructor
public class AccessStatusController {

    private final AllowedIpAccessService allowedIpAccessService;

    @GetMapping("/admin-status")
    public AdminStatusResponse getAdminStatus(@RequestHeader("X-Real-IP") String ip) {
        return new AdminStatusResponse(allowedIpAccessService.isAllowedAdmin(ip));
    }
}
