package com.example.flags;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlagEnvironmentValueRepository extends JpaRepository<FlagEnvironmentValue, UUID> {
    List<FlagEnvironmentValue> findByFlagIdIn(Collection<UUID> flagIds);
    Optional<FlagEnvironmentValue> findByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId);
}
