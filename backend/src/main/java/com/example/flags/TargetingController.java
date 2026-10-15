package com.example.flags;

import com.example.flags.TargetingDtos.TargetingDto;
import com.example.flags.TargetingDtos.UpdateTargetingRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/flags/{id}/environments/{envKey}/targeting")
public class TargetingController {

    private final TargetingService service;

    public TargetingController(TargetingService service) { this.service = service; }

    /** The target list holds user IDs, so viewers only see the counts in the flag list. */
    @GetMapping
    @PreAuthorize("hasRole('DEVELOPER')")
    public TargetingDto get(@PathVariable UUID id, @PathVariable String envKey) {
        return service.get(id, envKey);
    }

    /** Production needs release manager; the service enforces that. */
    @PutMapping
    @PreAuthorize("hasRole('DEVELOPER')")
    public TargetingDto replace(@PathVariable UUID id, @PathVariable String envKey,
                                @Valid @RequestBody UpdateTargetingRequest body) {
        return service.replace(id, envKey, new TargetingDto(body.userIds(), body.rules()), body.reason());
    }
}
