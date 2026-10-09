package dev.eolmae.marketry.runner;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.scheduler.MarketCalendarScheduler;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MarketCalendarStartupRunner implements ApplicationRunner {
    private final MarketCalendarScheduler scheduler;

    @Override
    public void run(ApplicationArguments args) {
        scheduler.initialize(LocalDate.now(Zone.KST.zoneId()));
    }
}
