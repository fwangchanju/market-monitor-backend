package dev.eolmae.marketmonitor.domain.notification.schedule;

import dev.eolmae.marketmonitor.domain.notification.enums.TelegramOverlap;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
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
        validate(telegramProperties.sendIntervalMinutes(), telegramProperties.sendMinute(), collectIntervalMinutes);
    }

    /**
     * 이번 tick에 발송해야 할 주기(분) 목록 — 발송하지 않으면 빈 리스트.
     * 기준점(그날 startHour:sendMinute)부터 경과분이 주기의 배수면 정규 발송, shouldCollect가 꺼진
     * 뒤 endHour:sendMinute 정각이면 가장 긴 주기 하나로 마감 리포트를 보낸다.
     */
    public List<Integer> due(LocalDateTime now, boolean shouldCollect) {
        return due(
                now,
                shouldCollect,
                startHour,
                endHour,
                telegramProperties.sendMinute(),
                telegramProperties.sendIntervalMinutes(),
                telegramProperties.overlap());
    }

    static List<Integer> due(
            LocalDateTime now,
            boolean shouldCollect,
            int startHour,
            int endHour,
            int sendMinute,
            List<Integer> cycles,
            TelegramOverlap overlap) {
        if (shouldCollect) {
            LocalDateTime baseTime = now.toLocalDate().atTime(startHour, sendMinute);
            long elapsedMinutes = Duration.between(baseTime, now).toMinutes();
            if (elapsedMinutes < 0) {
                return List.of();
            }
            List<Integer> due =
                    cycles.stream().filter(cycle -> elapsedMinutes % cycle == 0).toList();
            return applyOverlap(due, overlap);
        }

        boolean isClosingReportTime = now.getHour() == endHour && now.getMinute() == sendMinute;
        if (isClosingReportTime) {
            return List.of(Collections.max(cycles));
        }
        return List.of();
    }

    private static List<Integer> applyOverlap(List<Integer> due, TelegramOverlap overlap) {
        if (due.size() <= 1 || overlap == TelegramOverlap.ALL) {
            return due;
        }
        return List.of(Collections.max(due));
    }

    // 기동 실패 자체가 신호라 EscalateException을 쓰지 않는다 — @PostConstruct에서 던지면
    // EscalationPublisher를 거치지 않아 텔레그램 알림이 가지 않는데, EscalateException의 javadoc은
    // "발생 즉시 개발자에게 텔레그램 알림을 발송하는 예외"라 실제와 다르게 읽힌다.
    //
    // send-minute이 0이면 마감 리포트 조건(shouldCollect가 꺼지고 endHour:sendMinute)이 절대 성립하지
    // 않는다 — shouldCollect는 "시각 <= endHour:00"까지 true라서 20:00 정각엔 아직 꺼지지 않는다.
    // 배수 검증만으로는 0 % n == 0이라 통과하므로 별도 조건으로 막는다. 주기가 비어 있거나 0 이하인
    // 값을 포함하면 배수 검증은 통과하지만 due()의 나눗셈·Collections.max에서 런타임에 터진다.
    static void validate(List<Integer> cycles, int sendMinute, int intervalMinutes) {
        if (sendMinute <= 0) {
            throw new IllegalStateException("telegram.send-minute은 0보다 커야 함: " + sendMinute);
        }
        if (cycles.isEmpty()) {
            throw new IllegalStateException("telegram.send-interval-minutes가 비어 있음");
        }
        if (sendMinute % intervalMinutes != 0) {
            throw new IllegalStateException("telegram.send-minute(%d)이 collect.interval-minutes(%d)의 배수가 아님"
                    .formatted(sendMinute, intervalMinutes));
        }
        for (int cycle : cycles) {
            if (cycle <= 0) {
                throw new IllegalStateException("telegram.send-interval-minutes 값은 0보다 커야 함: " + cycle);
            }
            if (cycle % intervalMinutes != 0) {
                throw new IllegalStateException(
                        "telegram.send-interval-minutes 값(%d)이 collect.interval-minutes(%d)의 배수가 아님"
                                .formatted(cycle, intervalMinutes));
            }
        }
    }
}
