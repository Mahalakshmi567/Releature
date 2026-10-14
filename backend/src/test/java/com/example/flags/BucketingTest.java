package com.example.flags;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BucketingTest {

    @Test
    void sameFlagAndUserAlwaysGetTheSameBucket() {
        int first = Bucketing.bucket("new_checkout", "user-42");
        for (int i = 0; i < 100; i++) assertEquals(first, Bucketing.bucket("new_checkout", "user-42"));
    }

    @Test
    void bucketsStayWithinZeroToNinetyNine() {
        for (int i = 0; i < 5000; i++) {
            int b = Bucketing.bucket("new_checkout", "user-" + i);
            assertTrue(b >= 0 && b <= 99);
        }
    }

    @Test
    void tenPercentRolloutReachesRoughlyTenPercentOfUsers() {
        int inside = 0;
        for (int i = 0; i < 10_000; i++) if (Bucketing.bucket("new_checkout", "user-" + i) < 10) inside++;
        assertTrue(inside > 800 && inside < 1200, "inside = " + inside);
    }

    @Test
    void differentFlagsSplitUsersDifferently() {
        int differing = 0;
        for (int i = 0; i < 1000; i++)
            if (Bucketing.bucket("flag_a", "user-" + i) != Bucketing.bucket("flag_b", "user-" + i)) differing++;
        assertTrue(differing > 900, "differing = " + differing);
    }
}
