package com.example.flags;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private final AuditRepository repo = mock(AuditRepository.class);
    private final AuditService service = new AuditService(repo, new ObjectMapper());
    private final UUID flagId = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void recordsActorReasonAndBothConfigurations() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        service.record(flagId, "new_checkout", "New Checkout", "production", AuditAction.ENVIRONMENT_UPDATED,
                Map.of("value", "false"), Map.of("value", "true"), "  start the launch  ");

        ArgumentCaptor<AuditLogEntry> saved = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repo).save(saved.capture());
        AuditLogEntry e = saved.getValue();
        assertEquals("alice", e.getActor());
        assertEquals("start the launch", e.getReason());
        assertEquals("production", e.getEnvironmentKey());
        assertEquals("{\"value\":\"false\"}", e.getPreviousConfig());
        assertEquals("{\"value\":\"true\"}", e.getNewConfig());
        assertNotNull(e.getCreatedAt());
    }

    @Test
    void creationHasNoPreviousConfigurationAndDeletionHasNoNewOne() {
        service.record(flagId, "k", "n", null, AuditAction.FLAG_CREATED, null, Map.of("a", 1), "why");
        service.record(flagId, "k", "n", null, AuditAction.FLAG_DELETED, Map.of("a", 1), null, "why");

        ArgumentCaptor<AuditLogEntry> saved = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repo, times(2)).save(saved.capture());
        assertNull(saved.getAllValues().get(0).getPreviousConfig());
        assertNull(saved.getAllValues().get(1).getNewConfig());
    }

    @Test
    void aChangeThatLeavesTheConfigurationIdenticalIsNotRecorded() {
        service.record(flagId, "k", "n", "production", AuditAction.ENVIRONMENT_UPDATED,
                Map.of("value", "true"), Map.of("value", "true"), "no-op");
        verify(repo, never()).save(any());
    }

    @Test
    void aReasonIsRequired() {
        ApiException e = assertThrows(ApiException.class, () -> service.record(flagId, "k", "n", null,
                AuditAction.FLAG_UPDATED, Map.of("a", 1), Map.of("a", 2), "   "));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, e.getStatus());
        verify(repo, never()).save(any());
    }
}
