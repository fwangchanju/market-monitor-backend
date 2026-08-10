package dev.eolmae.marketmonitor.domain.notification.listener;

import dev.eolmae.marketmonitor.common.event.EscalationEvent;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** ESCALATION 로거로 파일 기록 + EscalationEvent 발행.
 * 수집기처럼 예외를 던지면 이후 실행이 멈추는 경우, 던지지 않고 이 메서드를 직접 호출해 동일한 처리를 받는다. */
@Component
@RequiredArgsConstructor
public class EscalationPublisher {

    private static final Logger ESCALATION_LOG = LoggerFactory.getLogger("ESCALATION");

    private final ApplicationEventPublisher eventPublisher;

    public void report(EscalateException e) {
        String logMessage = e.createLogMessage();
        ESCALATION_LOG.error(logMessage, e);
        eventPublisher.publishEvent(new EscalationEvent(logMessage));
    }
}
