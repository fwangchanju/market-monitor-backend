package dev.eolmae.marketmonitor.handler;

import dev.eolmae.marketmonitor.common.exception.BadRequestException;
import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.notification.listener.EscalationPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String ERROR_CODE_PROPERTY = "errorCode";
    private static final Duration SUPPRESSION_WINDOW = Duration.ofMinutes(5);

    private final EscalationPublisher escalationPublisher;

    // 같은 예외 클래스+메시지가 짧은 시간에 폭주해도(봇 스캔, 클라이언트 버그 등) 텔레그램은 5분에 1건만
    // 받는다. 억제된 횟수는 로그로만 남긴다.
    private final Map<String, Suppression> suppressionByKey = new ConcurrentHashMap<>();

    private record Suppression(Instant lastNotifiedAt, int suppressedCount) {}

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        HttpStatus status =
                switch (e) {
                    case BadRequestException ignored -> HttpStatus.BAD_REQUEST;
                    case NotFoundException ignored -> HttpStatus.NOT_FOUND;
                    case ConflictException ignored -> HttpStatus.CONFLICT;
                    case EscalateException ignored -> HttpStatus.INTERNAL_SERVER_ERROR;
                };

        if (e instanceof EscalateException escalateException) {
            escalationPublisher.report(escalateException);
        } else {
            log.error(e.createLogMessage(), e);
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

    /**
     * 처리되지 않은 나머지 전부(NPE 등 진짜 예상 못 한 예외)를 잡아 알림을 보낸다.
     * ErrorResponse 구현체(ResponseStatusException, NoResourceFoundException, HttpRequestMethodNotSupportedException
     * 등)와 HttpMessageNotReadableException·AsyncRequestNotUsableException(ErrorResponse는 아니지만 정상적인
     * 4xx/클라이언트 연결 끊김)은 그대로 다시 던져 Spring 기본 처리로 보낸다 — 안 그러면 nginx의 접근제어
     * 403(domain/access)이 500으로 바뀌고, 봇 스캔의 404 하나하나가 텔레그램 알림이 된다.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception e) throws Exception {
        if (isSpringHandledException(e)) {
            throw e;
        }

        reportOrSuppress(e);
        return toProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private boolean isSpringHandledException(Exception e) {
        return e instanceof ErrorResponse
                || e instanceof HttpMessageNotReadableException
                || e instanceof AsyncRequestNotUsableException;
    }

    private void reportOrSuppress(Exception e) {
        String key = e.getClass().getName() + "|" + e.getMessage();
        Instant now = Instant.now();

        Suppression updated = suppressionByKey.compute(key, (k, previous) -> {
            if (previous == null || now.isAfter(previous.lastNotifiedAt().plus(SUPPRESSION_WINDOW))) {
                return new Suppression(now, 0);
            }
            return new Suppression(previous.lastNotifiedAt(), previous.suppressedCount() + 1);
        });

        if (updated.lastNotifiedAt().equals(now)) {
            escalationPublisher.report(EscalateException.wrap(ErrorCode.INTERNAL_ERROR, e));
        } else {
            log.warn("[예상 못한 예외 알림 억제] | key : {} | 억제 누적 : {}건", key, updated.suppressedCount());
        }
    }

    private ProblemDetail toProblemDetail(HttpStatus status, ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty(ERROR_CODE_PROPERTY, errorCode.name());
        return problemDetail;
    }
}
