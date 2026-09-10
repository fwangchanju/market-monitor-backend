package dev.eolmae.marketmonitor.domain.notification.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.eolmae.marketmonitor.domain.notification.enums.TelegramOverlap;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegramSendScheduleTest {

    private static final int START_HOUR = 8;
    private static final int END_HOUR = 20;
    private static final int SEND_MINUTE = 10;
    private static final List<Integer> CYCLES = List.of(15, 120);
    private static final LocalDateTime DATE = LocalDateTime.of(2025, 6, 2, 0, 0);

    @Test
    void due_기준점_이전이라_아무_주기에도_걸리지_않는다() {
        LocalDateTime now = DATE.withHour(8).withMinute(0);

        assertThat(due(now, true, TelegramOverlap.ALL)).isEmpty();
        assertThat(due(now, true, TelegramOverlap.LONGEST_ONLY)).isEmpty();
    }

    @Test
    void due_기준_시각에는_겹치는_주기_전부가_걸린다() {
        LocalDateTime now = DATE.withHour(8).withMinute(10);

        assertThat(due(now, true, TelegramOverlap.ALL)).containsExactly(15, 120);
        assertThat(due(now, true, TelegramOverlap.LONGEST_ONLY)).containsExactly(120);
    }

    @Test
    void due_어느_주기의_간격에도_맞지_않는_시각은_발송되지_않는다() {
        LocalDateTime now = DATE.withHour(8).withMinute(20);

        // 경과분 10 — 15의 배수도 120의 배수도 아니다. 모듈로 판정이 깨지면 여기서 잡힌다.
        assertThat(due(now, true, TelegramOverlap.ALL)).isEmpty();
        assertThat(due(now, true, TelegramOverlap.LONGEST_ONLY)).isEmpty();
    }

    @Test
    void due_08시40분은_짧은_주기만_걸리고_긴_주기는_걸리지_않는다() {
        LocalDateTime now = DATE.withHour(8).withMinute(40);

        // 08:40 발송 복구 — startHour를 경계로 묶어 스킵하던 옛 버그가 여기서 사라졌는지 확인한다.
        assertThat(due(now, true, TelegramOverlap.ALL)).containsExactly(15);
        assertThat(due(now, true, TelegramOverlap.LONGEST_ONLY)).containsExactly(15);
    }

    @Test
    void due_두_시간_뒤에는_겹치는_주기_전부가_다시_걸린다() {
        LocalDateTime now = DATE.withHour(10).withMinute(10);

        assertThat(due(now, true, TelegramOverlap.ALL)).containsExactly(15, 120);
        assertThat(due(now, true, TelegramOverlap.LONGEST_ONLY)).containsExactly(120);
    }

    @Test
    void due_마감_시각에는_shouldCollect가_꺼져도_가장_긴_주기로_한_번_발송된다() {
        LocalDateTime now = DATE.withHour(20).withMinute(10);

        assertThat(due(now, false, TelegramOverlap.ALL)).containsExactly(120);
        assertThat(due(now, false, TelegramOverlap.LONGEST_ONLY)).containsExactly(120);
    }

    @Test
    void due_마감_이후_추가_사이클은_발송하지_않는다() {
        LocalDateTime now = DATE.withHour(20).withMinute(40);

        assertThat(due(now, false, TelegramOverlap.ALL)).isEmpty();
        assertThat(due(now, false, TelegramOverlap.LONGEST_ONLY)).isEmpty();
    }

    @Test
    void validate_sendMinute이_0이하이면_기동을_막는다() {
        assertThatThrownBy(() -> TelegramSendSchedule.validate(CYCLES, 0, 5)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_sendMinute이_collect_interval의_배수가_아니면_기동을_막는다() {
        assertThatThrownBy(() -> TelegramSendSchedule.validate(CYCLES, 7, 5)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_주기가_collect_interval의_배수가_아니면_기동을_막는다() {
        assertThatThrownBy(() -> TelegramSendSchedule.validate(List.of(15, 17), SEND_MINUTE, 5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_주기_목록이_비어_있으면_기동을_막는다() {
        assertThatThrownBy(() -> TelegramSendSchedule.validate(List.of(), SEND_MINUTE, 5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_주기에_0_이하_값이_있으면_기동을_막는다() {
        assertThatThrownBy(() -> TelegramSendSchedule.validate(List.of(0, 15), SEND_MINUTE, 5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_조건을_모두_만족하면_통과한다() {
        assertThatCode(() -> TelegramSendSchedule.validate(CYCLES, SEND_MINUTE, 5))
                .doesNotThrowAnyException();
    }

    private List<Integer> due(LocalDateTime now, boolean shouldCollect, TelegramOverlap overlap) {
        return TelegramSendSchedule.due(now, shouldCollect, START_HOUR, END_HOUR, SEND_MINUTE, CYCLES, overlap);
    }
}
