package com.example.flags;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlagConfigCacheTest {

    private static final String KEY = "flags:cfg:production:new_checkout";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final FlagConfigSnapshot snapshot = new FlagConfigSnapshot(
            "new_checkout", "production", FlagType.BOOLEAN, "false", "true", 25,
            Set.of("vip-1"), List.of(new FlagConfigSnapshot.Rule("plan", List.of("pro"))));

    @BeforeEach
    void setUp() { when(redis.opsForValue()).thenReturn(ops); }

    private FlagConfigCache cache(boolean enabled, Duration backoff) {
        return new FlagConfigCache(redis, mapper, enabled, Duration.ofSeconds(60), backoff);
    }

    @Test
    void storedSnapshotComesBackIntact() throws Exception {
        FlagConfigCache cache = cache(true, Duration.ofSeconds(10));
        cache.put("production", "new_checkout", snapshot);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq(KEY), json.capture(), eq(Duration.ofSeconds(60)));

        when(ops.get(KEY)).thenReturn(json.getValue());
        Optional<FlagConfigSnapshot> read = cache.get("production", "new_checkout");
        assertEquals(Optional.of(snapshot), read);
        assertEquals(1, cache.stats().hits());
    }

    @Test
    void missingEntryIsAMiss() {
        FlagConfigCache cache = cache(true, Duration.ofSeconds(10));
        when(ops.get(KEY)).thenReturn(null);
        assertTrue(cache.get("production", "new_checkout").isEmpty());
        assertEquals(1, cache.stats().misses());
    }

    @Test
    void redisFailureFallsBackAndStopsCallingRedisForAWhile() {
        FlagConfigCache cache = cache(true, Duration.ofSeconds(10));
        when(ops.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        assertTrue(cache.get("production", "new_checkout").isEmpty());
        assertTrue(cache.get("production", "new_checkout").isEmpty());

        verify(ops, times(1)).get(KEY); // the second call skipped Redis
        assertTrue(cache.stats().backingOff());
        assertEquals(1, cache.stats().errors());
    }

    @Test
    void evictDeletesTheKeyOfEveryEnvironmentGiven() {
        FlagConfigCache cache = cache(true, Duration.ofSeconds(10));
        cache.evict("new_checkout", List.of("development", "production"));

        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
        verify(redis).delete(keys.capture());
        assertEquals(Set.of("flags:cfg:development:new_checkout", KEY), Set.copyOf(keys.getValue()));
    }

    @Test
    void failedEvictionIsRetriedBeforeTheNextRead() {
        FlagConfigCache cache = cache(true, Duration.ZERO);
        when(redis.delete(anyCollection())).thenThrow(new RedisConnectionFailureException("down")).thenReturn(1L);
        when(ops.get(KEY)).thenReturn(null);

        cache.evict("new_checkout", List.of("production"));
        assertEquals(1, cache.stats().pendingEvictions());

        cache.get("production", "new_checkout");
        assertEquals(0, cache.stats().pendingEvictions());
        verify(redis, times(2)).delete(anyCollection());
    }

    @Test
    void aStaleEntryIsNeverServedWhileAnEvictionIsStillPending() {
        FlagConfigCache cache = cache(true, Duration.ZERO);
        when(redis.delete(anyCollection())).thenThrow(new RedisConnectionFailureException("down"));
        when(ops.get(KEY)).thenReturn("{\"stale\":true}");

        cache.evict("new_checkout", List.of("production"));
        assertTrue(cache.get("production", "new_checkout").isEmpty());
        verify(ops, never()).get(KEY);
    }

    @Test
    void disabledCacheNeverTouchesRedis() {
        FlagConfigCache cache = cache(false, Duration.ofSeconds(10));
        assertTrue(cache.get("production", "new_checkout").isEmpty());
        cache.put("production", "new_checkout", snapshot);
        cache.evict("new_checkout", List.of("production"));
        verifyNoInteractions(redis);
    }
}
