package dev.eolmae.marketmonitor.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.eolmae.marketmonitor.common.exception.BadRequestException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.listener.EscalationPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final EscalationPublisher escalationPublisher = Mockito.mock(EscalationPublisher.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(escalationPublisher);

    @Test
    void handleBusinessException_EscalateException은_알림을_보내고_500을_반환한다() {
        ProblemDetail result = handler.handleBusinessException(new EscalateException(ErrorCode.INTERNAL_ERROR));

        verify(escalationPublisher).report(Mockito.any());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    void handleBusinessException_일반_BusinessException은_알림을_보내지_않는다() {
        ProblemDetail result = handler.handleBusinessException(new BadRequestException(ErrorCode.INVALID_INPUT));

        verify(escalationPublisher, never()).report(Mockito.any());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void handleUnexpectedException_예상못한예외는_알림을_보내고_500을_반환한다() throws Exception {
        ProblemDetail result = handler.handleUnexpectedException(new NullPointerException("boom"));

        verify(escalationPublisher).report(Mockito.any());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getProperties()).containsEntry("errorCode", ErrorCode.INTERNAL_ERROR.name());
    }

    @Test
    void handleUnexpectedException_ResponseStatusException은_다시_던지고_알림을_보내지_않는다() {
        ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> handler.handleUnexpectedException(forbidden)).isSameAs(forbidden);

        verify(escalationPublisher, never()).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_HttpMessageNotReadableException은_다시_던지고_알림을_보내지_않는다() {
        HttpMessageNotReadableException e =
                new HttpMessageNotReadableException("malformed", Mockito.mock(HttpInputMessage.class));

        assertThatThrownBy(() -> handler.handleUnexpectedException(e)).isSameAs(e);

        verify(escalationPublisher, never()).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_AsyncRequestNotUsableException은_다시_던지고_알림을_보내지_않는다() {
        AsyncRequestNotUsableException e = new AsyncRequestNotUsableException("client disconnected");

        assertThatThrownBy(() -> handler.handleUnexpectedException(e)).isSameAs(e);

        verify(escalationPublisher, never()).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_같은_예외가_5분_내에_반복되면_두번째부터는_알림을_억제한다() throws Exception {
        handler.handleUnexpectedException(new IllegalStateException("반복"));
        handler.handleUnexpectedException(new IllegalStateException("반복"));

        verify(escalationPublisher, times(1)).report(Mockito.any());
    }

    @Test
    void handleUnexpectedException_예외_클래스나_메시지가_다르면_각각_알림을_보낸다() throws Exception {
        handler.handleUnexpectedException(new IllegalStateException("A"));
        handler.handleUnexpectedException(new IllegalStateException("B"));

        verify(escalationPublisher, times(2)).report(Mockito.any());
    }
}
