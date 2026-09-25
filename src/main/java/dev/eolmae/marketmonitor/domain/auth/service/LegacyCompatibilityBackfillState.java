package dev.eolmae.marketmonitor.domain.auth.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Keeps API traffic closed until the mandatory startup backfill has completed. */
@Component
public class LegacyCompatibilityBackfillState {

    private final AtomicBoolean complete = new AtomicBoolean();

    public boolean isComplete() {
        return complete.get();
    }

    void markComplete() {
        complete.set(true);
    }
}
