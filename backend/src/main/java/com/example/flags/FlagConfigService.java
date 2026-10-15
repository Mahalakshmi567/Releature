package com.example.flags;

import org.springframework.stereotype.Service;

/** Read-through access to flag configuration: Redis first, PostgreSQL on a miss. */
@Service
public class FlagConfigService {

    private final FlagConfigCache cache;
    private final FlagConfigLoader loader;

    public FlagConfigService(FlagConfigCache cache, FlagConfigLoader loader) {
        this.cache = cache;
        this.loader = loader;
    }

    /** Throws a 404 ApiException for an unknown flag or environment. Errors are never cached. */
    public FlagConfigSnapshot get(String envKey, String flagKey) {
        return cache.get(envKey, flagKey).orElseGet(() -> {
            FlagConfigSnapshot loaded = loader.load(envKey, flagKey);
            cache.put(envKey, flagKey, loaded);
            return loaded;
        });
    }
}
