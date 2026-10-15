package com.example.flags;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TargetingMatcherTest {

    @Test
    void matchesAnyAllowedValueIgnoringCase() {
        assertTrue(TargetingMatcher.matches("plan", List.of("pro", "enterprise"), Map.of("plan", "Pro")));
    }

    @Test
    void doesNotMatchOtherValues() {
        assertFalse(TargetingMatcher.matches("plan", List.of("pro", "enterprise"), Map.of("plan", "free")));
    }

    @Test
    void userWithoutTheAttributeNeverMatches() {
        assertFalse(TargetingMatcher.matches("plan", List.of("pro"), Map.of("country", "IN")));
    }
}
