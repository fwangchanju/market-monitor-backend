package dev.eolmae.marketmonitor.domain.access.controller;

import dev.eolmae.marketmonitor.domain.access.service.AdminTokenService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminTokenController {

    private final AdminTokenService adminTokenService;

    @GetMapping("/internal/register-ip")
    public ResponseEntity<Void> registerIp(@RequestParam String token, @RequestHeader("X-Real-IP") String currentIp) {
        adminTokenService.registerIp(token, currentIp);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
    }
}
