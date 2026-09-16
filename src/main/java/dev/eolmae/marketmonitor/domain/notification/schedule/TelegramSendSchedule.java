package dev.eolmae.marketmonitor.domain.notification.schedule;

import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
                telegramProperties.beforeMinutes(),
                telegramProperties.mapSendTimes(),
                collectIntervalMinutes,
                startHour,
                endHour);
    }

    /**
     * 지금 섹터 리포트를 보낼 때인가. 기준점(그날 startHour:sendMinute)부터 경과분이
     * send-interval-minutes의 배수인 tick에서만 보낸다. shouldCollect가 꺼진 뒤에는 그 경과분 계산이
     * 더 이상 의미가 없으므로(장중 수집이 멈춘 뒤라 데이터가 안 바뀜), endHour:sendMinute 정각 한
     * 번만 그날의 마지막 발송으로 고정한다 — 그 시각이 우연히 격자에 걸리는지 여부와 무관하게 항상
     * 정확히 한 번만 나가게 하기 위해서다.
     */
    public boolean due(LocalDateTime now, boolean shouldCollect) {
        return due(
                now,
                shouldCollect,
                startHour,
                endHour,
                telegramProperties.sendMinute(),
                telegramProperties.sendIntervalMinutes());
    }

    static boolean due(
            LocalDateTime now,
            boolean shouldCollect,
            int startHour,
            int endHour,
            int sendMinute,
            int sendIntervalMinutes) {
        if (shouldCollect) {
            LocalDateTime baseTime = now.toLocalDate().atTime(startHour, sendMinute);
            long elapsedMinutes = Duration.between(baseTime, now).toMinutes();
            if (elapsedMinutes < 0) {
                return false;
            }
            return elapsedMinutes % sendIntervalMinutes == 0;
        }

        return now.getHour() == endHour && now.getMinute() == sendMinute;
    }

    /**
     * 지금 맵 발송(코스피+코스닥 앨범) 시각인가. telegram.map-send-times에 정확히 일치하는 분에서만
     * 보낸다 — 격자·경과분 계산이 필요 없는 지정 시각 목록이라 due()와 판정 방식이 다르다.
     * shouldCollect가 꺼진 뒤에는 판정하지 않는다 — 장 마감 이후엔 맵 이미지도 더 이상 안 바뀐다.
     */
    public boolean dueForMap(LocalDateTime now, boolean shouldCollect) {
        return dueForMap(now, shouldCollect, telegramProperties.mapSendTimes());
    }

    static boolean dueForMap(LocalDateTime now, boolean shouldCollect, List<LocalTime> mapSendTimes) {
        if (!shouldCollect) {
            return false;
        }
        return mapSendTimes.contains(now.toLocalTime());
    }

    // 기동 실패 자체가 신호라 EscalateException을 쓰지 않는다 — @PostConstruct에서 던지면
    // EscalationPublisher를 거치지 않아 텔레그램 알림이 가지 않는데, EscalateException의 javadoc은
    // "발생 즉시 개발자에게 텔레그램 알림을 발송하는 예외"라 실제와 다르게 읽힌다.
    //
    // send-minute이 0이면 마감 조건(shouldCollect가 꺼지고 endHour:sendMinute)이 절대 성립하지 않는다
    // — shouldCollect는 "시각 <= endHour:00"까지 true라서 20:00 정각엔 아직 꺼지지 않는다.
    static void validate(
            int sendMinute,
            int sendIntervalMinutes,
            int beforeMinutes,
            List<LocalTime> mapSendTimes,
            int collectIntervalMinutes,
            int startHour,
            int endHour) {
        if (sendMinute <= 0) {
            throw new IllegalStateException("telegram.send-minute은 0보다 커야 함: " + sendMinute);
        }
        if (sendIntervalMinutes <= 0) {
            throw new IllegalStateException("telegram.send-interval-minutes는 0보다 커야 함: " + sendIntervalMinutes);
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
        if (mapSendTimes.isEmpty()) {
            throw new IllegalStateException("telegram.map-send-times는 비어 있으면 안 됨");
        }
        LocalTime rangeStart = LocalTime.of(startHour, 0);
        LocalTime rangeEnd = LocalTime.of(endHour, 0);
        for (LocalTime mapSendTime : mapSendTimes) {
            if (mapSendTime.getMinute() % collectIntervalMinutes != 0) {
                throw new IllegalStateException("telegram.map-send-times의 %s이 collect.interval-minutes(%d)의 배수가 아님"
                        .formatted(mapSendTime, collectIntervalMinutes));
            }
            if (mapSendTime.isBefore(rangeStart) || mapSendTime.isAfter(rangeEnd)) {
                throw new IllegalStateException(
                        "telegram.map-send-times의 %s이 collect.start-hour~end-hour 범위 밖임".formatted(mapSendTime));
            }
        }
    }
}
