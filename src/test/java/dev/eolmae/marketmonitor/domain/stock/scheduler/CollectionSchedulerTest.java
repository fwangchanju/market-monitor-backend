package dev.eolmae.marketmonitor.domain.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CollectionSchedulerTest {

    private static final int START_HOUR = 8;
    private static final int END_HOUR = 20;
    private static final int SEND_MINUTE = 10;
    private static final int SEND_INTERVAL_MINUTES = 30;

    @Test
    void isSendCycle_수집_시작_시각의_sendMinute는_발송된다() {
        boolean result = isSendCycle(SEND_MINUTE, START_HOUR);

        assertThat(result).isTrue();
    }

    @Test
    void isSendCycle_수집_시작_시각의_추가_사이클은_개장_전이라_발송되지_않는다() {
        // 08:10은 보내지만 30분 뒤 08:40은 아직 장이 안 열려서(개장 09:00) 데이터가 그대로라 건너뛴다.
        boolean result = isSendCycle(40, START_HOUR);

        assertThat(result).isFalse();
    }

    @Test
    void isSendCycle_수집_종료_시각의_sendMinute는_발송된다() {
        // 20:10은 마감(20:00) 직후 마지막 스냅샷을 알리는 마감 리포트라 그대로 보낸다.
        boolean result = isSendCycle(SEND_MINUTE, END_HOUR);

        assertThat(result).isTrue();
    }

    @Test
    void isSendCycle_수집_종료_시각의_추가_사이클은_마감_후_동일한_데이터라_발송되지_않는다() {
        // 20:00 마감 이후엔 dataTime이 20:00으로 고정되므로, 20:40은 20:10과 완전히 같은 내용을 중복 발송하게 된다.
        boolean result = isSendCycle(40, END_HOUR);

        assertThat(result).isFalse();
    }

    @Test
    void isSendCycle_시작과_종료_사이_시각은_sendMinute와_그_다음_간격_둘_다_발송된다() {
        int marketOpenHour = START_HOUR + 1;

        assertThat(isSendCycle(SEND_MINUTE, marketOpenHour)).as("09:10").isTrue();
        assertThat(isSendCycle(40, marketOpenHour)).as("09:40").isTrue();
    }

    @Test
    void isSendCycle_간격에_맞지_않는_시각은_발송되지_않는다() {
        boolean result = isSendCycle(25, START_HOUR + 1);

        assertThat(result).isFalse();
    }

    @Test
    void isSendCycle_sendMinute보다_이른_시각은_발송되지_않는다() {
        boolean result = isSendCycle(5, START_HOUR + 1);

        assertThat(result).isFalse();
    }

    private boolean isSendCycle(int minute, int hour) {
        return CollectionScheduler.isSendCycle(minute, hour, START_HOUR, END_HOUR, SEND_MINUTE, SEND_INTERVAL_MINUTES);
    }
}
