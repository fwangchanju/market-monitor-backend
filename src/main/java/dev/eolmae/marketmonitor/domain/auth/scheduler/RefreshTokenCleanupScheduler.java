package dev.eolmae.marketmonitor.domain.auth.scheduler;

import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.auth.service.AuthService;
import dev.eolmae.marketmonitor.domain.notification.listener.EscalationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료·폐기된 갱신 토큰 정리 배치. 삭제 로직은 {@link AuthService}에 두고, 여기서는 언제 돌지만 담당한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private static final String KST_ZONE_ID = "Asia/Seoul";

    private final AuthService authService;
    private final EscalationPublisher escalationPublisher;

    @Scheduled(cron = "0 10 4 * * *", zone = KST_ZONE_ID)
    public void cleanupRefreshTokens() {
        try {
            authService.cleanupRefreshTokens();
        } catch (Exception e) {
            escalationPublisher.report(EscalateException.wrap(ErrorCode.REFRESH_TOKEN_CLEANUP_FAILED, e));
        }
    }
}
