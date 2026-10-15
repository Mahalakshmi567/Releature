package com.example.flags;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TargetRuleRepository extends JpaRepository<TargetRule, UUID> {
    List<TargetRule> findByEnvironmentValueId(UUID environmentValueId);
    void deleteByEnvironmentValueId(UUID environmentValueId);

    @Query("select r.environmentValueId, count(r) from TargetRule r where r.environmentValueId in :ids group by r.environmentValueId")
    List<Object[]> countByEnvironmentValueIds(@Param("ids") Collection<UUID> ids);
}
