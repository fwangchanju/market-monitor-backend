package dev.eolmae.marketmonitor.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/** Runs the required V2-era data repair before the application finishes initializing. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyCompatibilityBackfillInitializer implements SmartInitializingSingleton {

    private final LegacyCompatibilityBackfillService backfillService;
    private final LegacyCompatibilityBackfillState backfillState;

    @Override
    public void afterSingletonsInstantiated() {
        backfillService.reconcile();
        backfillState.markComplete();
        log.info("[startup] Legacy compatibility data backfill completed");
    }
}
