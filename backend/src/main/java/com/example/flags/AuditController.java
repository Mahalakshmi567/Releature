package com.example.flags;

import com.example.flags.AuditDtos.AuditPage;
import com.example.flags.AuditDtos.RollbackPreview;
import com.example.flags.AuditDtos.RollbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;
    private final RollbackService rollbacks;

    public AuditController(AuditService service, RollbackService rollbacks) {
        this.service = service;
        this.rollbacks = rollbacks;
    }

    /** Newest first. Filter by flag key and/or environment key. */
    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    public AuditPage list(@RequestParam(required = false) String flagKey,
                          @RequestParam(required = false) String environment,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "25") int size) {
        return service.search(flagKey, environment, page, size);
    }

    /** What rolling back this entry would change, or why it can't be rolled back. */
    @GetMapping("/{id}/rollback")
    @PreAuthorize("hasRole('VIEWER')")
    public RollbackPreview rollbackPreview(@PathVariable UUID id) { return rollbacks.preview(id); }

    /**
     * Restores the configuration from before this entry's change, recorded as a new audit entry. The same rules as
     * a normal edit apply: restoring production needs release manager (enforced where the change is applied).
     */
    @PostMapping("/{id}/rollback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DEVELOPER')")
    public void rollback(@PathVariable UUID id, @Valid @RequestBody RollbackRequest request) {
        rollbacks.rollback(id, request.reason());
    }
}
