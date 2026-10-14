package com.example.flags;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks how explicit targeting interacts with the percentage rollout. Environment value = "true", default = "false". */
class EvaluationServiceTest {

    private final FlagConfigService configs = mock(FlagConfigService.class);
    private final EvaluationService service = new EvaluationService(configs);

    private void configure(int rolloutPercentage) {
        when(configs.get("production", "new_checkout")).thenReturn(new FlagConfigSnapshot(
                "new_checkout", "production", FlagType.BOOLEAN, "false", "true", rolloutPercentage,
                Set.of("vip-1"), List.of(new FlagConfigSnapshot.Rule("plan", List.of("pro", "enterprise")))));
    }

    private EvaluationResponse eval(String userId, Map<String, String> attributes) {
        return service.evaluate("production", "new_checkout", userId, attributes);
    }

    @Test
    void explicitUserGetsTheValueEvenAtZeroPercent() {
        configure(0);
        EvaluationResponse r = eval("vip-1", Map.of());
        assertEquals("true", r.value());
        assertEquals("EXPLICIT_USER", r.reason());
    }

    @Test
    void attributeRuleGetsTheValueEvenAtZeroPercent() {
        configure(0);
        EvaluationResponse r = eval("user-9", Map.of("Plan", "Pro"));
        assertEquals("true", r.value());
        assertEquals("ATTRIBUTE_RULE", r.reason());
        assertEquals("plan is one of pro, enterprise", r.matchedRule());
    }

    @Test
    void attributeRuleWorksWithoutAUserId() {
        configure(0);
        assertEquals("ATTRIBUTE_RULE", eval(null, Map.of("plan", "enterprise")).reason());
    }

    @Test
    void userWhoIsNotTargetedFollowsTheRollout() {
        configure(0);
        EvaluationResponse r = eval("user-9", Map.of("plan", "free"));
        assertEquals("false", r.value());
        assertEquals("NOT_ROLLED_OUT", r.reason());
    }

    @Test
    void notTargetedUserAtPartialRolloutUsesTheirBucket() {
        configure(50);
        EvaluationResponse r = eval("user-9", Map.of());
        boolean inside = Bucketing.bucket("new_checkout", "user-9") < 50;
        assertEquals(inside ? "true" : "false", r.value());
        assertEquals(inside ? "IN_ROLLOUT" : "OUTSIDE_ROLLOUT", r.reason());
    }

    @Test
    void userWithoutAttributeIsNotMatchedByRule() {
        configure(0);
        assertEquals("NOT_ROLLED_OUT", eval("user-9", Map.of("country", "IN")).reason());
    }

    @Test
    void sameUserGetsTheSameDecisionEveryTime() {
        configure(30);
        String first = eval("user-123", Map.of()).value();
        for (int i = 0; i < 50; i++) assertEquals(first, eval("user-123", Map.of()).value());
    }
}
