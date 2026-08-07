package dev.eolmae.marketmonitor.handler;

import dev.eolmae.marketmonitor.common.event.EscalationEvent;
import dev.eolmae.marketmonitor.common.exception.BadRequestException;
import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String ERROR_CODE_PROPERTY = "errorCode";

    private final ApplicationEventPublisher eventPublisher;

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        HttpStatus status =
                switch (e) {
                    case BadRequestException ignored -> HttpStatus.BAD_REQUEST;
                    case NotFoundException ignored -> HttpStatus.NOT_FOUND;
                    case ConflictException ignored -> HttpStatus.CONFLICT;
                    case EscalateException ignored -> HttpStatus.INTERNAL_SERVER_ERROR;
                };

        String logMessage = e.createLogMessage();
        log.error(logMessage, e);
        if (e instanceof EscalateException) {
            eventPublisher.publishEvent(new EscalationEvent(logMessage));
        }

        return toProblemDetail(status, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn(detail);

        return toProblemDetail(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, detail);
    }

    private ProblemDetail toProblemDetail(HttpStatus status, ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty(ERROR_CODE_PROPERTY, errorCode.name());
        return problemDetail;
    }
}
