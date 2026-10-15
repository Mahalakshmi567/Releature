package com.example.flags;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers recent attempts per key (here, a client address) and says when there have been too many inside a
 * time window. It lives in memory, so it resets when the backend restarts and is not shared between instances.
 */
public class AttemptLimiter {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final int max;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public AttemptLimiter(int max, Duration window) { this(max, window, Clock.systemUTC()); }

    AttemptLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        Deque<Long> times = attempts.get(key);
        if (times == null) return false;
        synchronized (times) {
            prune(times);
            return times.size() >= max;
        }
    }

    public void record(String key) {
        Deque<Long> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            prune(times);
            times.addLast(clock.millis());
        }
        if (attempts.size() > SWEEP_THRESHOLD) {
            attempts.values().removeIf(q -> {
                synchronized (q) {
                    prune(q);
                    return q.isEmpty();
                }
            });
        }
    }

    public void clear(String key) { attempts.remove(key); }

    private void prune(Deque<Long> times) {
        long cutoff = clock.millis() - windowMillis;
        while (!times.isEmpty() && times.peekFirst() <= cutoff) times.pollFirst();
    }
}
