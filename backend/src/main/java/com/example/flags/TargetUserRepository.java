package com.example.flags;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TargetUserRepository extends JpaRepository<TargetUser, UUID> {
    boolean existsByEnvironmentValueIdAndUserId(UUID environmentValueId, String userId);
    List<TargetUser> findByEnvironmentValueId(UUID environmentValueId);
    void deleteByEnvironmentValueId(UUID environmentValueId);

    @Query("select t.environmentValueId, count(t) from TargetUser t where t.environmentValueId in :ids group by t.environmentValueId")
    List<Object[]> countByEnvironmentValueIds(@Param("ids") Collection<UUID> ids);
}
