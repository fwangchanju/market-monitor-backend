package dev.eolmae.marketmonitor.domain.notification.schedule;

import dev.eolmae.marketmonitor.domain.notification.enums.TelegramSendKind;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 리포트 발송 시각 판정 — "지금 발송할 때인가"를 답한다. collect.* 두 값은 수집 격자에 맞추기
 * 위한 제약일 뿐이라 CollectionScheduler와는 별개로 각자 주입받는다. 스프링 없이 단위 테스트할 수 있게
 * 실제 판정 로직은 인자를 그대로 받는 static 메서드로 뺐다.
 */
@Component
@RequiredArgsConstructor
public class TelegramSendSchedule {

    private final TelegramProperties telegramProperties;

    @Value("${collect.start-hour}")
    private int startHour;

    @Value("${collect.end-hour}")
    private int endHour;

    @Value("${collect.interval-minutes}")
    private int collectIntervalMinutes;

    @PostConstruct
    void validateConfiguration() {
        validate(
                telegramProperties.sendMinute(),
                telegramProperties.sendIntervalMinutes(),
                telegramProperties.mapIntervalMinutes(),
                telegramProperties.beforeMinutes(),
                collectIntervalMinutes);
    }

    /**
     * 이번 tick에 무엇을 보낼지 — NONE(안 보냄), SECTOR_ONLY(섹터만), WITH_MAP(맵 포함).
     * 기준점(그날 startHour:sendMinute)부터 경과분이 send-interval-minutes의 배수인 tick에서만
     * 보내고, 그중에서도 map-interval-minutes의 배수인 tick만 맵을 포함한다. shouldCollect가 꺼진
     * 뒤 endHour:sendMinute 정각이면 그날 마지막 메시지이므로 WITH_MAP으로 고정한다.
     */
    public TelegramSendKind due(LocalDateTime now, boolean shouldCollect) {
        return due(
                now,
                shouldCollect,
                startHour,
                endHour,
                telegramProperties.sendMinute(),
                telegramProperties.sendIntervalMinutes(),
                telegramProperties.mapIntervalMinutes());
    }

    static TelegramSendKind due(
            LocalDateTime now,
            boolean shouldCollect,
            int startHour,
            int endHour,
            int sendMinute,
            int sendIntervalMinutes,
            int mapIntervalMinutes) {
        if (shouldCollect) {
            LocalDateTime baseTime = now.toLocalDate().atTime(startHour, sendMinute);
            long elapsedMinutes = Duration.between(baseTime, now).toMinutes();
            if (elapsedMinutes < 0) {
                return TelegramSendKind.NONE;
            }
            if (elapsedMinutes % sendIntervalMinutes != 0) {
                return TelegramSendKind.NONE;
            }
            return elapsedMinutes % mapIntervalMinutes == 0 ? TelegramSendKind.WITH_MAP : TelegramSendKind.SECTOR_ONLY;
        }

        boolean isClosingReportTime = now.getHour() == endHour && now.getMinute() == sendMinute;
        return isClosingReportTime ? TelegramSendKind.WITH_MAP : TelegramSendKind.NONE;
    }

    // 기동 실패 자체가 신호라 EscalateException을 쓰지 않는다 — @PostConstruct에서 던지면
    // EscalationPublisher를 거치지 않아 텔레그램 알림이 가지 않는데, EscalateException의 javadoc은
    // "발생 즉시 개발자에게 텔레그램 알림을 발송하는 예외"라 실제와 다르게 읽힌다.
    //
    // send-minute이 0이면 마감 리포트 조건(shouldCollect가 꺼지고 endHour:sendMinute)이 절대 성립하지
    // 않는다 — shouldCollect는 "시각 <= endHour:00"까지 true라서 20:00 정각엔 아직 꺼지지 않는다.
    // map-interval-minutes가 send-interval-minutes의 배수가 아니면 맵 포함 tick이 발송 격자와 어긋나
    // 맵이 영영 안 나가거나 엉뚱한 시각에 나간다.
    static void validate(
            int sendMinute,
            int sendIntervalMinutes,
            int mapIntervalMinutes,
            int beforeMinutes,
            int collectIntervalMinutes) {
        if (sendMinute <= 0) {
            throw new IllegalStateException("telegram.send-minute은 0보다 커야 함: " + sendMinute);
        }
        if (sendIntervalMinutes <= 0) {
            throw new IllegalStateException("telegram.send-interval-minutes는 0보다 커야 함: " + sendIntervalMinutes);
        }
        if (mapIntervalMinutes <= 0) {
            throw new IllegalStateException("telegram.map-interval-minutes는 0보다 커야 함: " + mapIntervalMinutes);
        }
        if (beforeMinutes <= 0) {
            throw new IllegalStateException("telegram.before-minutes는 0보다 커야 함: " + beforeMinutes);
        }
        if (sendMinute % collectIntervalMinutes != 0) {
            throw new IllegalStateException("telegram.send-minute(%d)이 collect.interval-minutes(%d)의 배수가 아님"
                    .formatted(sendMinute, collectIntervalMinutes));
        }
        if (sendIntervalMinutes % collectIntervalMinutes != 0) {
            throw new IllegalStateException("telegram.send-interval-minutes(%d)이 collect.interval-minutes(%d)의 배수가 아님"
                    .formatted(sendIntervalMinutes, collectIntervalMinutes));
        }
        if (beforeMinutes % collectIntervalMinutes != 0) {
            throw new IllegalStateException("telegram.before-minutes(%d)가 collect.interval-minutes(%d)의 배수가 아님"
                    .formatted(beforeMinutes, collectIntervalMinutes));
        }
        if (mapIntervalMinutes % sendIntervalMinutes != 0) {
            throw new IllegalStateException(
                    "telegram.map-interval-minutes(%d)가 telegram.send-interval-minutes(%d)의 배수가 아님"
                            .formatted(mapIntervalMinutes, sendIntervalMinutes));
        }
    }
}
