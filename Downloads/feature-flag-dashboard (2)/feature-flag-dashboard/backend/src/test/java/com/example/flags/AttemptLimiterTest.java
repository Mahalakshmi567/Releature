package com.example.flags;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AttemptLimiterTest {

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final AttemptLimiter limiter = new AttemptLimiter(3, Duration.ofMinutes(10), clock);

    @Test
    void blocksOnceTheLimitIsReachedInsideTheWindow() {
        limiter.record("1.2.3.4");
        limiter.record("1.2.3.4");
        assertFalse(limiter.isBlocked("1.2.3.4"));
        limiter.record("1.2.3.4");
        assertTrue(limiter.isBlocked("1.2.3.4"));
    }

    @Test
    void otherClientsAreNotAffected() {
        for (int i = 0; i < 3; i++) limiter.record("1.2.3.4");
        assertFalse(limiter.isBlocked("5.6.7.8"));
    }

    @Test
    void oldAttemptsExpire() {
        for (int i = 0; i < 3; i++) limiter.record("1.2.3.4");
        clock.advance(Duration.ofMinutes(11));
        assertFalse(limiter.isBlocked("1.2.3.4"));
    }

    @Test
    void clearingForgetsTheClient() {
        for (int i = 0; i < 3; i++) limiter.record("1.2.3.4");
        limiter.clear("1.2.3.4");
        assertFalse(limiter.isBlocked("1.2.3.4"));
    }
}
