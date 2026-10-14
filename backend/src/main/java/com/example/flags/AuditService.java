package com.example.flags;

import com.example.flags.AuditDtos.AuditEntryResponse;
import com.example.flags.AuditDtos.AuditPage;
import com.example.flags.AuditDtos.RolledBackEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditService {

    static final int MAX_REASON = 500;

    private final AuditRepository repo;
    private final ObjectMapper mapper;

    public AuditService(AuditRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public void record(UUID flagId, String flagKey, String flagName, String environmentKey, AuditAction action,
                       Object previous, Object next, String reason) {
        record(flagId, flagKey, flagName, environmentKey, action, previous, next, reason, null);
    }

    /**
     * Records who changed what. Call it inside the same transaction as the change, so the change and its
     * history are saved together or not at all. A change that leaves the configuration identical is not recorded.
     * {@code rollbackOf} is the id of the entry being undone when the change is a rollback, otherwise null.
     */
    @Transactional
    public void record(UUID flagId, String flagKey, String flagName, String environmentKey, AuditAction action,
                       Object previous, Object next, String reason, UUID rollbackOf) {
        String cleanReason = cleanReason(reason);
        JsonNode before = previous == null ? null : mapper.valueToTree(previous);
        JsonNode after = next == null ? null : mapper.valueToTree(next);
        if (before != null && after != null && before.equals(after)) return;
        repo.save(new AuditLogEntry(flagId, flagKey, flagName, environmentKey, action, currentActor(), cleanReason,
                before == null ? null : before.toString(), after == null ? null : after.toString(), rollbackOf));
    }

    @Transactional(readOnly = true)
    public AuditPage search(String flagKey, String environment, int page, int size) {
        Specification<AuditLogEntry> spec = Specification.where(null);
        if (flagKey != null && !flagKey.isBlank())
            spec = spec.and((root, q, cb) -> cb.equal(root.get("flagKey"), flagKey));
        if (environment != null && !environment.isBlank())
            spec = spec.and((root, q, cb) -> cb.equal(root.get("environmentKey"), environment));
        Page<AuditLogEntry> result = repo.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<UUID, AuditLogEntry> restored = repo.findAllById(result.getContent().stream()
                        .map(AuditLogEntry::getRollbackOf).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(AuditLogEntry::getId, e -> e));

        return new AuditPage(result.getContent().stream().map(e -> toResponse(e, restored)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    static String cleanReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "A reason for the change is required.");
        if (r.length() > MAX_REASON)
            throw new ApiException(HttpStatus.BAD_REQUEST, "The reason can be at most " + MAX_REASON + " characters.");
        return r;
    }

    static String currentActor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a instanceof AnonymousAuthenticationToken || a.getName() == null) return "system";
        return a.getName();
    }

    private AuditEntryResponse toResponse(AuditLogEntry e, Map<UUID, AuditLogEntry> restored) {
        AuditLogEntry original = e.getRollbackOf() == null ? null : restored.get(e.getRollbackOf());
        return new AuditEntryResponse(e.getId(), e.getFlagId(), e.getFlagKey(), e.getFlagName(),
                e.getEnvironmentKey(), e.getAction(), e.getActor(), e.getReason(),
                parse(e.getPreviousConfig()), parse(e.getNewConfig()), e.getCreatedAt(),
                original == null ? null : new RolledBackEntry(original.getId(), original.getActor(), original.getCreatedAt()));
    }

    private JsonNode parse(String json) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored audit entry is not valid JSON", ex);
        }
    }
}
