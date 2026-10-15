package com.example.flags;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis cache of flag configuration, one entry per flag and environment.
 *
 * The cache is an optimisation, never a dependency: if Redis is down or slow, every method here swallows the error
 * and the caller reads from PostgreSQL instead. After a failure Redis is skipped for a short while, so evaluations
 * do not each wait for a timeout.
 */
@Component
public class FlagConfigCache {

    private static final Logger log = LoggerFactory.getLogger(FlagConfigCache.class);
    private static final String PREFIX = "flags:cfg:";

    public record CacheStats(boolean enabled, boolean backingOff, long hits, long misses, long errors,
                             long ttlSeconds, int pendingEvictions) {}

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration ttl;
    private final Duration backoff;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    /** Keys whose deletion Redis has not confirmed yet. They are retried before the next cache read. */
    private final Set<String> pendingEvictions = ConcurrentHashMap.newKeySet();
    private volatile long skipUntil = 0;

    @Autowired
    public FlagConfigCache(StringRedisTemplate redis, ObjectMapper mapper,
                           @Value("${app.cache.enabled:true}") boolean enabled,
                           @Value("${app.cache.ttl-seconds:60}") long ttlSeconds) {
        this(redis, mapper, enabled, Duration.ofSeconds(ttlSeconds), Duration.ofSeconds(10));
    }

    FlagConfigCache(StringRedisTemplate redis, ObjectMapper mapper, boolean enabled, Duration ttl, Duration backoff) {
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
        this.ttl = ttl;
        this.backoff = backoff;
    }

    public Optional<FlagConfigSnapshot> get(String envKey, String flagKey) {
        if (!usable() || !flushPending()) return Optional.empty();
        try {
            String json = redis.opsForValue().get(key(envKey, flagKey));
            if (json == null) {
                misses.incrementAndGet();
                return Optional.empty();
            }
            FlagConfigSnapshot snapshot = mapper.readValue(json, FlagConfigSnapshot.class);
            hits.incrementAndGet();
            return Optional.of(snapshot);
        } catch (JsonProcessingException e) {
            // An entry we cannot read (for example written by another version) counts as a miss and gets overwritten.
            log.warn("Ignoring unreadable cache entry for {}/{}", envKey, flagKey);
            misses.incrementAndGet();
            return Optional.empty();
        } catch (RuntimeException e) {
            markDown(e);
            return Optional.empty();
        }
    }

    public void put(String envKey, String flagKey, FlagConfigSnapshot snapshot) {
        if (!usable()) return;
        try {
            redis.opsForValue().set(key(envKey, flagKey), mapper.writeValueAsString(snapshot), ttl);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise flag configuration for {}/{}", envKey, flagKey, e);
        } catch (RuntimeException e) {
            markDown(e);
        }
    }

    /** Drops the cached copies of a flag in the given environments. */
    public void evict(String flagKey, Collection<String> envKeys) {
        if (!enabled) return;
        envKeys.forEach(env -> pendingEvictions.add(key(env, flagKey)));
        if (usable() && !flushPending()) {
            log.error("Could not evict flag '{}' from Redis; the old value can be served for up to {}s", flagKey, ttl.toSeconds());
        }
    }

    public CacheStats stats() {
        return new CacheStats(enabled, System.currentTimeMillis() < skipUntil, hits.get(), misses.get(),
                errors.get(), ttl.toSeconds(), pendingEvictions.size());
    }

    private boolean usable() { return enabled && System.currentTimeMillis() >= skipUntil; }

    /** Deletes the keys still waiting for eviction. Returns false if Redis could not be reached. */
    private boolean flushPending() {
        if (pendingEvictions.isEmpty()) return true;
        List<String> batch = List.copyOf(pendingEvictions);
        try {
            redis.delete(batch);
            pendingEvictions.removeAll(batch);
            return true;
        } catch (RuntimeException e) {
            markDown(e);
            return false;
        }
    }

    private void markDown(RuntimeException e) {
        errors.incrementAndGet();
        skipUntil = System.currentTimeMillis() + backoff.toMillis();
        log.warn("Redis is unavailable; reading flags from the database for the next {}s ({})",
                backoff.toSeconds(), e.getMessage());
    }

    private static String key(String envKey, String flagKey) { return PREFIX + envKey + ":" + flagKey; }
}
