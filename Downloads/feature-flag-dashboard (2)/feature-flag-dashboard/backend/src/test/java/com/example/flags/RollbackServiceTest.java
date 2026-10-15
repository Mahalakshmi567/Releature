package com.example.flags;

import com.example.flags.AuditDtos.RollbackPreview;
import com.example.flags.FlagDtos.UpdateFlagRequest;
import com.example.flags.TargetingDtos.TargetingDto;
import com.example.flags.TargetingDtos.TargetingRuleDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RollbackServiceTest {

    private final AuditRepository audit = mock(AuditRepository.class);
    private final FlagRepository flags = mock(FlagRepository.class);
    private final EnvironmentRepository environments = mock(EnvironmentRepository.class);
    private final FlagEnvironmentValueRepository values = mock(FlagEnvironmentValueRepository.class);
    private final FlagService flagService = mock(FlagService.class);
    private final TargetingService targetingService = mock(TargetingService.class);
    private final RollbackService service = new RollbackService(
            audit, flags, environments, values, flagService, targetingService, new ObjectMapper());

    private final UUID entryId = UUID.randomUUID();
    private final UUID flagId = UUID.randomUUID();
    private final UUID envId = UUID.randomUUID();

    private AuditLogEntry entry(AuditAction action, String env, String previous, String next) {
        AuditLogEntry e = mock(AuditLogEntry.class);
        when(e.getId()).thenReturn(entryId);
        when(e.getFlagId()).thenReturn(flagId);
        when(e.getFlagKey()).thenReturn("new_checkout");
        when(e.getFlagName()).thenReturn("New Checkout");
        when(e.getEnvironmentKey()).thenReturn(env);
        when(e.getAction()).thenReturn(action);
        when(e.getPreviousConfig()).thenReturn(previous);
        when(e.getNewConfig()).thenReturn(next);
        when(audit.findById(entryId)).thenReturn(Optional.of(e));
        return e;
    }

    private Flag flagExists(String name, String description, String defaultValue) {
        Flag f = mock(Flag.class);
        when(f.getId()).thenReturn(flagId);
        when(f.getName()).thenReturn(name);
        when(f.getKey()).thenReturn("new_checkout");
        when(f.getDescription()).thenReturn(description);
        when(f.getDataType()).thenReturn(FlagType.BOOLEAN);
        when(f.getDefaultValue()).thenReturn(defaultValue);
        when(flags.findById(flagId)).thenReturn(Optional.of(f));
        return f;
    }

    private void currentEnvironmentValue(String value, int rollout) {
        Environment env = mock(Environment.class);
        when(env.getId()).thenReturn(envId);
        when(environments.findByKey("production")).thenReturn(Optional.of(env));
        FlagEnvironmentValue v = new FlagEnvironmentValue();
        v.setValue(value);
        v.setRolloutPercentage(rollout);
        when(values.findByFlagIdAndEnvironmentId(flagId, envId)).thenReturn(Optional.of(v));
    }

    @Test
    void environmentRollbackRestoresValueAndRolloutAndLinksTheEntry() {
        entry(AuditAction.ENVIRONMENT_UPDATED, "production",
                "{\"value\":\"false\",\"rolloutPercentage\":0}", "{\"value\":\"true\",\"rolloutPercentage\":100}");
        flagExists("New Checkout", null, "false");
        currentEnvironmentValue("true", 100);

        service.rollback(entryId, "  broke checkout  ");

        verify(flagService).setEnvironmentValue(flagId, "production", "false", 0, "broke checkout", entryId);
    }

    @Test
    void flagEditRollbackRestoresNameDescriptionAndDefault() {
        entry(AuditAction.FLAG_UPDATED, null,
                "{\"name\":\"Old name\",\"key\":\"new_checkout\",\"description\":null,\"dataType\":\"BOOLEAN\",\"defaultValue\":\"false\"}",
                "{\"name\":\"New name\",\"key\":\"new_checkout\",\"description\":\"d\",\"dataType\":\"BOOLEAN\",\"defaultValue\":\"true\"}");
        flagExists("New name", "d", "true");

        service.rollback(entryId, "undo");

        verify(flagService).update(flagId, new UpdateFlagRequest("Old name", "false", null, "undo"), entryId);
    }

    @Test
    void targetingRollbackRestoresUsersAndRules() {
        entry(AuditAction.TARGETING_UPDATED, "production",
                "{\"userIds\":[\"u1\"],\"rules\":[{\"attribute\":\"plan\",\"values\":[\"pro\"]}]}",
                "{\"userIds\":[],\"rules\":[]}");
        flagExists("New Checkout", null, "false");
        when(targetingService.get(flagId, "production")).thenReturn(new TargetingDto(List.of(), List.of()));

        service.rollback(entryId, "undo");

        verify(targetingService).replace(flagId, "production",
                new TargetingDto(List.of("u1"), List.of(new TargetingRuleDto("plan", List.of("pro")))), "undo", entryId);
    }

    @Test
    void previewWarnsWhenTheConfigurationChangedAfterTheEntry() {
        entry(AuditAction.ENVIRONMENT_UPDATED, "production",
                "{\"value\":\"false\",\"rolloutPercentage\":0}", "{\"value\":\"true\",\"rolloutPercentage\":10}");
        flagExists("New Checkout", null, "false");
        currentEnvironmentValue("true", 50); // someone raised the rollout after the entry

        RollbackPreview p = service.preview(entryId);

        assertTrue(p.possible());
        assertTrue(p.changedSince());
        assertEquals(50, p.current().get("rolloutPercentage").asInt());
        assertEquals(0, p.restoreTo().get("rolloutPercentage").asInt());
    }

    @Test
    void previewDoesNotWarnWhenNothingChangedSince() {
        entry(AuditAction.ENVIRONMENT_UPDATED, "production",
                "{\"value\":\"false\",\"rolloutPercentage\":0}", "{\"value\":\"true\",\"rolloutPercentage\":100}");
        flagExists("New Checkout", null, "false");
        currentEnvironmentValue("true", 100);

        assertFalse(service.preview(entryId).changedSince());
    }

    @Test
    void rollingBackToTheCurrentConfigurationIsRejected() {
        entry(AuditAction.ENVIRONMENT_UPDATED, "production",
                "{\"value\":\"false\",\"rolloutPercentage\":0}", "{\"value\":\"true\",\"rolloutPercentage\":100}");
        flagExists("New Checkout", null, "false");
        currentEnvironmentValue("false", 0); // already rolled back

        ApiException e = assertThrows(ApiException.class, () -> service.rollback(entryId, "again"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verifyNoInteractions(flagService, targetingService);
    }

    @Test
    void deletedFlagsCannotBeRestored() {
        entry(AuditAction.ENVIRONMENT_UPDATED, "production",
                "{\"value\":\"false\",\"rolloutPercentage\":0}", "{\"value\":\"true\",\"rolloutPercentage\":100}");
        when(flags.findById(flagId)).thenReturn(Optional.empty());

        assertFalse(service.preview(entryId).possible());
        ApiException e = assertThrows(ApiException.class, () -> service.rollback(entryId, "undo"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void creationAndDeletionEntriesCannotBeRolledBack() {
        entry(AuditAction.FLAG_DELETED, null, "{\"name\":\"x\"}", null);
        flagExists("New Checkout", null, "false");

        ApiException e = assertThrows(ApiException.class, () -> service.rollback(entryId, "undo"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        verifyNoInteractions(flagService, targetingService);
    }

    @Test
    void aReasonIsRequired() {
        entry(AuditAction.ENVIRONMENT_UPDATED, "production",
                "{\"value\":\"false\",\"rolloutPercentage\":0}", "{\"value\":\"true\",\"rolloutPercentage\":100}");
        ApiException e = assertThrows(ApiException.class, () -> service.rollback(entryId, "  "));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        verifyNoInteractions(flagService, targetingService);
    }

    @Test
    void unknownEntryIsNotFound() {
        when(audit.findById(entryId)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.preview(entryId));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }
}
