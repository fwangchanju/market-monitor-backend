package dev.eolmae.marketmonitor.domain.access.controller;

import dev.eolmae.marketmonitor.domain.access.dto.AllowedIpItem;
import dev.eolmae.marketmonitor.domain.access.service.AllowedIpService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 일반(USER) 허용 IP 관리. 이 경로 자체가 nginx에서 admin scope로 보호됨. */
@RestController
@RequestMapping("/api/admin/allowed-ips")
@RequiredArgsConstructor
public class AllowedIpController {

    private final AllowedIpService allowedIpService;

    @GetMapping
    public List<AllowedIpItem> list() {
        return allowedIpService.list();
    }

    @PostMapping("/{ip}")
    public void register(@PathVariable String ip) {
        allowedIpService.register(ip);
    }

    @DeleteMapping("/{ip}")
    public void delete(@PathVariable String ip) {
        allowedIpService.delete(ip);
    }
}
