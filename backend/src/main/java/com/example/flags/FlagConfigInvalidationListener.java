package com.example.flags;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts after the commit, not before. Evicting earlier would let a reader that runs in between cache
 * the old database state again.
 */
@Component
public class FlagConfigInvalidationListener {

    private final FlagConfigCache cache;

    public FlagConfigInvalidationListener(FlagConfigCache cache) { this.cache = cache; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChange(FlagConfigChangedEvent event) {
        cache.evict(event.flagKey(), event.environmentKeys());
    }
}
