package com.example.flags;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FlagRepository extends JpaRepository<Flag, UUID> {
    boolean existsByKey(String key);
    Optional<Flag> findByKey(String key);
}
