package com.example.flags;

import com.example.flags.FlagDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flags")
public class FlagController {

    private final FlagService service;

    public FlagController(FlagService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    public List<FlagResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    public FlagResponse get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DEVELOPER')")
    public FlagResponse create(@Valid @RequestBody CreateFlagRequest request) { return service.create(request); }

    /** Changing the default value also needs the release manager role; the service enforces that. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public FlagResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateFlagRequest request) {
        return service.update(id, request);
    }

    /** Developers can change development and testing; production needs release manager (enforced in the service). */
    @PutMapping("/{id}/environments/{envKey}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public FlagResponse setEnvironmentValue(@PathVariable UUID id, @PathVariable String envKey,
                                            @Valid @RequestBody UpdateEnvironmentValueRequest request) {
        return service.setEnvironmentValue(id, envKey, request.value(), request.rolloutPercentage(), request.reason());
    }

    /** Deleting removes the flag from production too, so it needs the release manager role. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('RELEASE_MANAGER')")
    public void delete(@PathVariable UUID id, @RequestParam String reason) { service.delete(id, reason); }
}
