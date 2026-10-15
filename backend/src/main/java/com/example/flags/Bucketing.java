package com.example.flags;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic bucketing. The same flag key and user id always map to the same bucket (0-99),
 * on any server and across restarts. A user is inside a rollout of N% when bucket < N, so raising
 * the percentage only ever adds users; nobody who already has the feature loses it.
 * The flag key is part of the hash, so different flags split users differently.
 */
public final class Bucketing {
    private Bucketing() {}

    public static int bucket(String flagKey, String userId) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest((flagKey + ":" + userId).getBytes(StandardCharsets.UTF_8));
            long n = 0;
            for (int i = 0; i < 8; i++) n = (n << 8) | (d[i] & 0xFFL);
            return (int) Long.remainderUnsigned(n, 100);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
