package com.example.flags;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlagConfigServiceTest {

    private final FlagConfigCache cache = mock(FlagConfigCache.class);
    private final FlagConfigLoader loader = mock(FlagConfigLoader.class);
    private final FlagConfigService service = new FlagConfigService(cache, loader);
    private final FlagConfigSnapshot snapshot = new FlagConfigSnapshot(
            "new_checkout", "production", FlagType.BOOLEAN, "false", "true", 100, Set.of(), List.of());

    @Test
    void cacheHitDoesNotTouchTheDatabase() {
        when(cache.get("production", "new_checkout")).thenReturn(Optional.of(snapshot));
        assertSame(snapshot, service.get("production", "new_checkout"));
        verifyNoInteractions(loader);
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void cacheMissLoadsFromTheDatabaseAndStoresTheResult() {
        when(cache.get("production", "new_checkout")).thenReturn(Optional.empty());
        when(loader.load("production", "new_checkout")).thenReturn(snapshot);
        assertSame(snapshot, service.get("production", "new_checkout"));
        verify(cache).put("production", "new_checkout", snapshot);
    }

    @Test
    void unknownFlagsAreNotCached() {
        when(cache.get("production", "nope")).thenReturn(Optional.empty());
        when(loader.load("production", "nope")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Flag not found."));
        assertThrows(ApiException.class, () -> service.get("production", "nope"));
        verify(cache, never()).put(any(), any(), any());
    }
}
