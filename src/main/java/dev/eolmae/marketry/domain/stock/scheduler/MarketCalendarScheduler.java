package dev.eolmae.marketry.domain.stock.scheduler;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.collector.MarketCalendarCollector;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCalendarScheduler {
    private final MarketCalendarCollector collector;
    private final MarketCalendarService service;
    private LocalDate attemptDate;
    private boolean succeeded;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void refresh() {
        refresh(LocalDate.now(Zone.KST.zoneId()));
    }

    public synchronized void refresh(LocalDate date) {
        attemptDate = date;
        succeeded = collector.collect(date);
    }

    @Scheduled(cron = "0 5,15 6 * * *", zone = "Asia/Seoul")
    public void retry() {
        retry(LocalDate.now(Zone.KST.zoneId()));
    }

    public synchronized void retry(LocalDate date) {
        if (date.equals(attemptDate) && succeeded == false) {
            succeeded = collector.collect(date);
        }
    }

    public synchronized void initialize(LocalDate date) {
        try {
            if (service.findByCountryAndDate(Country.KR, date)
                    .filter(calendar -> calendar.getStatus() != MarketCalendarStatus.FAILED)
                    .isPresent()) {
                return;
            }
        } catch (Exception e) {
            log.warn("[시작 시 시장 시간표 조회 실패] | context : KR|{}", date);
        }
        refresh(date);
    }
}
