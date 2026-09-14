package dev.eolmae.marketmonitor.domain.notification.listener;

import dev.eolmae.marketmonitor.common.event.EscalationEvent;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 예외 로그(root를 타고 EXCEPTION_FILE로) 기록 + EscalationEvent 발행.
 * 수집기처럼 예외를 던지면 이후 실행이 멈추는 경우, 던지지 않고 이 메서드를 직접 호출해 동일한 처리를 받는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscalationPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void report(EscalateException e) {
        String logMessage = e.createLogMessage();
        log.error(logMessage, e);
        eventPublisher.publishEvent(new EscalationEvent(logMessage + e.getCauseMessage()));
    }
}
