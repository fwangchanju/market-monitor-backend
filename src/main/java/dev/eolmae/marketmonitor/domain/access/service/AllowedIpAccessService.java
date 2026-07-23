package dev.eolmae.marketmonitor.domain.access.service;

import dev.eolmae.marketmonitor.common.cache.CacheKey;
import dev.eolmae.marketmonitor.config.ApplicationConfig;
import dev.eolmae.marketmonitor.domain.access.enums.Role;
import dev.eolmae.marketmonitor.domain.access.repository.AllowedIpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** IP 허용 여부 판정. nginx auth_request가 매 요청마다 호출하므로 캐시로 DB 부하를 줄인다. */
@Service
@RequiredArgsConstructor
public class AllowedIpAccessService {

    private final AllowedIpRepository allowedIpRepository;

    @Cacheable(cacheNames = CacheKey.ALLOWED_IP, cacheManager = ApplicationConfig.ACCESS_CACHE_MANAGER)
    @Transactional(readOnly = true)
    public boolean isAllowed(String ip) {
        return allowedIpRepository.existsById(ip);
    }

    @Transactional(readOnly = true)
    public boolean isAllowedAdmin(String ip) {
        return allowedIpRepository.existsByIpAndRole(ip, Role.ADMIN);
    }

    @CacheEvict(cacheNames = CacheKey.ALLOWED_IP, cacheManager = ApplicationConfig.ACCESS_CACHE_MANAGER)
    public void invalidate(String ip) {}
}
