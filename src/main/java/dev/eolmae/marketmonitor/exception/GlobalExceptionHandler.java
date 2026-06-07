package dev.eolmae.marketmonitor.exception;

import dev.eolmae.marketmonitor.domain.notification.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AlertService alertService;

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(EscalateException.class)
    public ErrorResponse handleEscalateException(EscalateException e) {
        log.error("[ESCALATE] {}", e.getMessage(), e);
        alertService.sendEscalation(e);
        return new ErrorResponse(e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BusinessException.class)
    public ErrorResponse handleBusinessException(BusinessException e) {
        log.error("[BUSINESS] {}", e.getMessage(), e);
        return new ErrorResponse(e.getMessage());
    }
}
