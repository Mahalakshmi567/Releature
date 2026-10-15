package com.example.flags;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cache")
@PreAuthorize("hasRole('DEVELOPER')")
public class CacheController {

    private final FlagConfigCache cache;

    public CacheController(FlagConfigCache cache) { this.cache = cache; }

    /** Hits, misses and Redis errors since the backend started. */
    @GetMapping("/stats")
    public FlagConfigCache.CacheStats stats() { return cache.stats(); }
}
