package com.example.flags;

import com.example.flags.TargetingDtos.EvaluateRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluate")
@PreAuthorize("hasRole('VIEWER')")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) { this.service = service; }

    /** Attributes are passed as attr.name=value, e.g. ?userId=u1&attr.plan=pro */
    @GetMapping("/{envKey}/{flagKey}")
    public EvaluationResponse evaluate(@PathVariable String envKey, @PathVariable String flagKey,
                                       @RequestParam(required = false) String userId,
                                       @RequestParam Map<String, String> params) {
        Map<String, String> attributes = new HashMap<>();
        params.forEach((k, v) -> { if (k.startsWith("attr.")) attributes.put(k.substring(5), v); });
        return service.evaluate(envKey, flagKey, userId, attributes);
    }

    @PostMapping("/{envKey}/{flagKey}")
    public EvaluationResponse evaluate(@PathVariable String envKey, @PathVariable String flagKey,
                                       @RequestBody EvaluateRequest body) {
        return service.evaluate(envKey, flagKey, body.userId(), body.attributes());
    }
}
