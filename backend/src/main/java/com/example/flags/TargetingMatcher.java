package com.example.flags;

import java.util.Collection;
import java.util.Map;

final class TargetingMatcher {
    private TargetingMatcher() {}

    /**
     * True when the user has the attribute and its value is one of the allowed values (case-insensitive).
     * A user without the attribute never matches. Attribute names must already be lower-case.
     */
    static boolean matches(String attribute, Collection<String> allowedValues, Map<String, String> attributes) {
        String actual = attributes.get(attribute);
        if (actual == null) return false;
        return allowedValues.stream().anyMatch(v -> v.equalsIgnoreCase(actual));
    }
}
