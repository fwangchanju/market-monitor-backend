package dev.eolmae.marketmonitor.handler;

import dev.eolmae.marketmonitor.common.event.EscalationEvent;
import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ErrorResponse;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApplicationEventPublisher eventPublisher;

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(EscalateException.class)
    public ErrorResponse handleEscalateException(EscalateException e) {
        String message = e.createMessage();
        log.error(message);
        eventPublisher.publishEvent(new EscalationEvent(message));

        return ErrorResponse.of(e.getErrorCode(), message);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BusinessException.class)
    public ErrorResponse handleBusinessException(BusinessException e) {
        String message = e.createMessage();
        log.error(message);

        return ErrorResponse.of(e.getErrorCode(), message);
    }
}
